# Server Tracker API Plan

Status: **implemented** (2026-09-23) — all four public routes are live through
`TrackerController`: cached statistics, paginated servers, server detail, and tracked-player
sightings. The implementation adds `TrackerQueryService`, four response records, filtered
repository queries, and `V49` indexes; focused unit tests and a PostgreSQL/HTTP smoke pass.

---

## 1. Goals & scope

Expose the persisted tracker dataset through the public HTTP API. Statistics and three read
endpoints are live over the existing `tracker_servers` and `tracker_player_history` tables:

- a bounded, deterministic paginated server collection;
- one canonical server-detail resource keyed by tracker UUID;
- one tracked-player resource listing the servers where that UUID was observed.

The API remains a read model: requests never trigger a network ping, refresh claim, DNS lookup,
or geo lookup. Player data is limited to identity-verified status samples already persisted by
the tracker. Historical online-count series, live-session tracking, username search, and direct
persistence-entity responses are out of scope.

**Top-level counters**

| Counter | Definition |
|---|---|
| `trackedServers` | total tracked servers |
| `trackedPlayers` | distinct players observed by the tracker |
| `onlinePlayers` | distinct players present in the latest player sample of alive servers |

The headline is *verified*, not advertised: the server-controlled `online_count` can be
falsified (fake counts, inflated lists for bait), so only players actually **observed in a
sample** count. Every `tracker_player_history` row is identity-verified upstream (the
`fake_identity`/`unverified` sample gate), and `last_seen` ≈ the server's `last_updated`
(120s grace for the store flush window) ties a player to the freshest absorbed sample.
Players drop out at the first refresh whose sample omits them (refresh cadence is hours).


**Breakdowns (top 10 by server count each)**

| Field | Key |
|---|---|
| `geo` | country ISO code (`VARCHAR(2)`); unknown-country rows omitted |
| `platform` | server-software string from version name (`Paper`, `Spigot`, `Forge`, plain version → `unknown`), **keys lowercased** so one canonical bucket per software; `Platform` enum not involved (edition vs software) |
| `protocol` | status-protocol number (JSON keys render as strings); unknown rows omitted |

Repo constraints: no `pom.xml` changes; strict Controller → Service → Repository layering;
record DTOs; explicit `{}` braces.

---

## 2. Endpoints

### Implemented — statistics

```
GET /tracker/stats
```

JSON (camelCase):

```json
{
  "trackedServers": 48213,
  "trackedPlayers": 1923553,
  "onlinePlayers": 10487,
  "geo": { "US": 10234, "DE": 4300, "FR": 1200 },
  "platform": { "paper": 20000, "vanilla": 8000, "forge": 2500 },
  "protocol": { "769": 30000, "767": 5000, "763": 2100 }
}
```

Response record: `model/dto/response/TrackerStatsResponse(long trackedServers, long trackedPlayers, long onlinePlayers, Map<String, Long> geo, Map<String, Long> platform, Map<String, Long> protocol)`.

Caching: `CacheControl.maxAge(60, SECONDS).cachePublic()` — the snapshot refreshes every
`stats.refresh-seconds` (300) and after refresh chunks; a 60s public cache is safe and absorbs
traffic spikes (mirrors `ServerController` cache-control usage).

Controller: `controller/TrackerController` exposes the statistics and read routes.

### Public read APIs

| Method | Route | Result |
|---|---|---|
| `GET` | `/tracker/servers?page=1` | Fixed-size page of recently updated servers |
| `GET` | `/tracker/{uuid}` | One server's public tracker telemetry |
| `GET` | `/tracker/players/{uuid}?page=1` | Fixed-size page of a player's tracked-server sightings |
| `GET` | `/tracker/players?query=Notch` | Username-prefix autocomplete for players with server history |

These routes extend the existing controller without changing the response or cache behavior of
`GET /tracker/stats`. Detailed contracts live in §4.

---

## 3. Implemented stats design — `TrackerStatsService`

Follows the existing `StatisticsService` pattern (in-memory counters, async DB load):

- `AtomicLong` per counter (`trackedServers`, `trackedPlayers`, `onlinePlayers`) + a cached
  `Map<String, Long>` per breakdown (geo, platform, protocol) — all held as one immutable
  snapshot so a request sees consistent values.
- Load once at `ApplicationReadyEvent` via `CompletableFuture.supplyAsync(..., Main.EXECUTOR)` —
  **never touch repositories in `@PostConstruct`**: `StatisticsService` documents the
  singleton-lock deadlock this caused at startup (late singleton resolution on virtual threads).
  Same trap applies here.
- Refresh cadence (configurable, default 300 s): a scheduled task recomputes counters and
  breakdowns in one `CompletableFuture` fan-out. Also refresh after each refresh chunk
  completes (counts churn during a discovery campaign; the refresh cycle moves the numbers in
  steady state).
  - `COUNT(DISTINCT player_uuid)` over tens of millions of rows is **not** per-request work →
    everything is always served from memory; the endpoint never touches the DB. The three
    GROUP-BY breakdowns are cheap (~100k-row seq scans) and run only on refresh.
  - If a refresh is already running when the scheduler fires, skip (no overlapping refreshes).
- Scheduling runs only when tracking is enabled (`mc-utils.server-tracker.tracking.enabled`).

**Behavior when tracking is disabled:** the endpoint stays up and returns empty/zero state
(`"geo": {}`, counters 0) — no 404/503 churn; the values simply never move.

**Components**

| Layer | File | Notes |
|---|---|---|
| Controller | `controller/TrackerController.java` | one `@GetMapping`, Swagger tag/description |
| DTO | `model/dto/response/TrackerStatsResponse.java` | record, camelCase |
| Service | `service/TrackerStatsService.java` | counters + breakdowns, async load, scheduled + cycle-triggered refresh |
| Repository | `repository/postgres/ServerTrackerRepository.java` | count/group queries plus filtered detail/list lookups |
| Repository | `repository/postgres/PlayerHistoryRepository.java` | distinct-player count and paged joined sightings |
| Metric | `ServerTrackerMetric` | `tracker_servers` gauge fed by the same counters (`docs/SERVER_TRACKER_PLAN.md` §10) |


---

## 4. Implemented read API design

The three routes are database-backed snapshots. They never join the discovery/refresh loops and
never expose `TrackedServerRow` or `PlayerHistoryRow` directly to the controller.

### 4.1 Shared contract

| Concern | Contract |
|---|---|
| Base path | `/tracker` |
| Representation | JSON, camelCase record fields |
| Time values | ISO-8601 UTC `Instant` values |
| UUID representation | Canonical lower-case hyphenated UUID in responses; accept the 32- and 36-character forms already supported by `UUIDUtils.parseUuid` on input |
| Pagination | One-based `page`, fixed **50 items per page**, no client-controlled page size; reuse `Pagination.Page<T>` |
| Ordering | Every ordered query has a unique tie-breaker so one row cannot move between pages |
| Empty state | For a collection, page 1 returns `Pagination.empty()` with `items: []`; any other page is 400. Unknown UUID resources return 404 |
| Invalid input | Invalid UUID or page values return 400 through the existing `ErrorResponse` advice |
| Caching | Successful GET responses use `Cache-Control: public, max-age=60` |
| Feature state | When `mc-utils.server-tracker.tracking.enabled=false`, the collection is empty and detail/player lookups return 404 without reading persisted rows; `/stats` retains its existing startup snapshot behavior |

`onlineCount`, `version`, `platform`, `protocol`, and MOTD are server-advertised and therefore
untrusted. The API must not present `onlineCount` as the verified player count used by
`/stats`; the field name and OpenAPI description must say it is advertised by the server.

### 4.2 Paginated servers — `GET /tracker/servers`

**Query parameters**

| Name | Type | Default | Rules |
|---|---|---|---|
| `page` | integer | `1` | One-based page within the filtered result set; invalid/out-of-range values return 400 |
| `ip` | string | — | Case-insensitive IP prefix |
| `country` | string | — | Case-insensitive country-code contains match |
| `platform` | string | — | Case-insensitive platform contains match |
| `protocol` | integer | — | Exact status-protocol number |
| `minOnlinePlayers` | integer | `0` | Inclusive lower bound on advertised online players |
| `maxOnlinePlayers` | integer | unlimited | Inclusive upper bound on advertised online players |
| `sort` | enum | `lastUpdated` | One of `lastUpdated`, `onlineCount`, `latencyMs`, `protocol`, `ip`, `country` |
| `direction` | `asc`, `desc` | `desc` | Direction for the selected sort field |

Filters combine with AND. Player bounds must be non-negative and `minOnlinePlayers` cannot exceed
`maxOnlinePlayers`. Sort fields use an explicit allowlist and every ordering has a stable tie-breaker.
Rows use the existing pagination envelope and a compact summary DTO:

```json
{
  "items": [
    {
      "uuid": "0195b8c0-69d4-7cc2-a1a4-a8b99e5eaf10",
      "ip": "198.51.100.10",
      "port": 25565,
      "version": "Paper 1.21.4",
      "protocol": 769,
      "platform": "Paper",
      "onlineCount": 18,
      "maxPlayers": 100,
      "country": "US",
      "online": true,
      "lastUpdated": "2026-09-23T10:15:30Z"
    }
  ],
  "totalItems": 48213,
  "itemsPerPage": 50,
  "totalPages": 965
}
```

`online` reflects the latest tracker refresh result. It is not real-time reachability;
`lastUpdated` remains the authoritative freshness timestamp. The collection omits MOTD and the
detail-only safety/geo fields to keep list payloads small.

**Data access:** `ServerTrackerRepository` builds a JPA Specification from the normalized filters
and resolves sorting through the allowlist. Filtered `totalItems` and `totalPages` come from the same
paged query contract, so client pagination remains consistent with active filters.

### 4.3 Server detail — `GET /tracker/{uuid}`

The implemented detail lookup accepts only the canonical tracker UUID. IP/port aliases would add
ambiguous lookups and a second cache-key shape, so they remain deferred.

`TrackedServerDetailResponse` contains the summary fields plus:

The `protocol` field remains numeric in API responses but is not rendered anywhere in the website.
The UUID remains the API resource key but is not rendered as a profile field either.

| Field | Source / meaning |
|---|---|
| `firstSeen` | First successful discovery verification |
| `lastCheckedAt` | `last_refreshed`: latest refresh attempt, successful or not |
| `motd` | Cleaned, length-limited, server-controlled text; never render as trusted HTML |
| `latencyMs` | Observed status-ping latency |
| `modded` | Tracker-derived modded flag |
| `preventsChatReports` | Server-advertised secure-chat capability |
| `enforcesSecureChat` | Server-advertised secure-chat capability |
| `previewsChat` | Server-advertised chat-preview capability |
| `asn` | Geo-enriched autonomous-system number, nullable |

Do not expose `motdHash`, `faviconHash`, or `sampleCount`; they are internal correlation/detector
fields rather than endpoint data. Unavailable UUIDs return the standard 404 `ErrorResponse`.
Tracking-disabled lookups also return 404 without reading older database rows.

### 4.4 Search tracked players — `GET /tracker/players`

The `query` parameter is a case-insensitive username prefix. The endpoint returns at most ten
matches and only includes players who have server-tracker history:

```json
[
  {
    "playerUuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
    "username": "Notch",
    "skinId": 42
  }
]
```

Responses use `Cache-Control: no-store`. A blank query or disabled tracking returns an empty list.

### 4.5 Tracked player — `GET /tracker/players/{uuid}`

This is a reverse lookup over `tracker_player_history`, not a replacement for the existing
`/players/{id}` profile API. The path accepts a player UUID only: UUIDs are rename-proof, while
username lookup would make the resource identity change over time.

The response contains the canonical player UUID and the same fixed-size pagination envelope used
by the server collection:

```json
{
  "playerUuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "servers": {
    "items": [
      {
        "username": "ImFascinated",
        "firstSeen": "2026-08-01T03:00:00Z",
        "lastSeen": "2026-09-23T10:15:30Z",
        "timesSeen": 4,
        "server": {
          "uuid": "0195b8c0-69d4-7cc2-a1a4-a8b99e5eaf10",
          "ip": "198.51.100.10",
          "port": 25565,
          "version": "Paper 1.21.4",
          "protocol": 769,
          "platform": "Paper",
          "onlineCount": 18,
          "maxPlayers": 100,
          "country": "US",
          "online": true,
          "lastUpdated": "2026-09-23T10:15:30Z"
        }
      }
    ],
    "totalItems": 2,
    "itemsPerPage": 50,
    "totalPages": 1
  }
}
```

Sightings are ordered by `last_seen DESC, server_uuid ASC`. Each item keeps the username observed
on that server because refresh times can temporarily leave different last-known names on two
servers for the same player UUID. Do not synthesize a global username or a player-presence flag:
the schema stores observations, not continuous sessions. `server.online` describes only the
server's latest refresh result; it does not prove that this player is in the latest sample. Player
UUIDs with no available sightings return 404, while mixed histories return only available server
sightings. An invalid UUID returns 400. This endpoint adds no collection or retention and exposes
no player IP, session timeline, or identity metadata beyond the persisted sighting.

### 4.6 Layering and DTOs

`TrackerQueryService` owns row-to-DTO mapping and keeps the stats-specific
`TrackerStatsService` focused on its cached aggregate snapshot:

```text
TrackerController → TrackerQueryService → ServerTrackerRepository
                                  └────→ PlayerHistoryRepository
```

Response records in `model/dto/response`:

| DTO | Purpose |
|---|---|
| `TrackedServerSummaryResponse` | Shared compact server representation for list and player sightings |
| `TrackedPlayerSearchResponse` | Username-prefix autocomplete result with player UUID and skin ID |
| `TrackedServerDetailResponse` | Full public detail, mapped explicitly from `TrackedServerRow` |
| `TrackedPlayerResponse` | Player UUID plus paginated sightings |
| `TrackedPlayerServerResponse` | Per-server username, first/last seen, times seen, and server summary |

`TrackerController` receives both `TrackerStatsService` and `TrackerQueryService`; the controller
performs no JPA work and receives no persistence entity. `PlayerHistoryRepository` exposes a
paged joined projection and matching count, so player lookup never issues a query per server. Its
projection interface follows the existing `ServerTrackerRepository.Breakdown` repository pattern.

### 4.7 Indexes and rollout-safe queries

`V49__tracker_api_indexes.sql` adds the access paths used by the server list and player lookup,
and removes the redundant single-column player index. The server index supports recent-first
pagination, while the replacement player index supports per-player counts and ordered sightings.
Do not add speculative indexes for deferred client filters. The scratch smoke applied V49 to
PostgreSQL 18 and verified the resulting definitions; production rollout should still schedule
index creation during a safe write window.

No new application configuration was required. Page size and cache TTL stay fixed constants so
clients cannot create high-cardinality or abusive query shapes.

---

## 5. Configuration

The endpoints add no `application.yml` keys. They reuse the existing tables, records, pagination
helper, exception advice, and `mc-utils.server-tracker.tracking.enabled` state. Only the stats
snapshot has a refresh setting:

```yaml
mc-utils.server-tracker.stats.refresh-seconds: 300
```

Keeping page size and cache TTL fixed avoids a configuration surface that has no operational
need. Add keys only if measured traffic requires a different bound.

---

## 6. Verification

- Focused Maven verification: `TrackerStatsServiceTest`, `TrackerQueryServiceTest`, and
  `TrackerControllerTest` — 22 tests, 0 failures/errors.
- PostgreSQL 18 smoke: Flyway applies V49 and V50; the collection/player indexes and focused
  filter/sort indexes exist, while `idx_tracker_player_history_player` is absent.
- HTTP smoke with multiple server and player records: stats, collection, and detail returned the
  expected available records; unavailable details and UUIDs without sightings returned 404.
- Username autocomplete returned a player with history and excluded an otherwise indexed player
  without sightings; selecting the match opened its history page successfully.
- Filter smoke combined location, platform, protocol, status, and player bounds; verified stable
  player-count sorting, IP-prefix filtering, filtered page bounds, 400 validation, V50 indexes,
  URL round-tripping, active chips, and responsive filter controls.
- Successful reads returned the documented cache headers; invalid page and UUID inputs returned 400.
- With tracking disabled, the collection returned an empty page and detail/player lookups returned
  404 without repository reads.

No online-history endpoint is part of this API: `V46__drop_tracker_server_online_history.sql`
removed that table, so a history contract would require a separate persistence decision.

## 7. Implementation record

1. Updated stats count, player, and breakdown queries with their visibility rules.
2. Added the five public response records and explicit service mappers.
3. Added public collection/player indexes in `V49` and focused filter/sort indexes in `V50`.
4. Added filtered server specifications, allow-listed sorting, tracked-player search, and joined
   player-sighting/count projections.
5. Implemented `TrackerQueryService` with parsing, search, filtered pagination, feature-state
   handling, visibility filtering, and not-found behavior.
6. Wired all four read routes into `TrackerController` with the documented cache policy.
7. Added focused unit coverage and verified the API against scratch PostgreSQL over HTTP.