# Server Tracker API Plan

Status: **implemented** (2026-09-20) — `GET /servers/tracker/stats` is live (TrackerController +
TrackerStatsService, cached snapshot, 60s public cache), backed by the tracked-server pipeline
(`docs/SERVER_TRACKER_PLAN.md`, `V43__server_tracker.sql`, refresh cycle). This document covers
the public API surface.

---

## 1. Goals & scope

Expose tracker telemetry over the public HTTP API. **For now: stats only** — one endpoint
serving three counters plus **geo, platform and protocol breakdowns** (all tracked by the
pipeline per `docs/SERVER_TRACKER_PLAN.md` (§4 schema, §6 geo/platform/protocol). The endpoint
is the first consumer of the dataset.

**Top-level counters**

| Counter | Definition | Source query |
|---|---|---|
| `trackedServers` | total servers in `tracker_servers` (incl. honeypot-flagged) | `SELECT COUNT(*) FROM tracker_servers` |
| `trackedPlayers` | distinct players ever seen across all tracked servers | `SELECT COUNT(DISTINCT player_uuid) FROM tracker_player_history` |
| `onlinePlayers` | sum of `online_count` over **alive, non-honeypot** servers | `SELECT COALESCE(SUM(online_count), 0) FROM tracker_servers WHERE consecutive_offline = 0 AND NOT honeypot` |

Why exclude honeypots from `onlinePlayers`: their counts are fabricated sample-bait; including
them pollutes the headline number. `trackedServers` still counts them (they are tracked and
flagged — that flag is part of the dataset).

**Breakdowns (top 10 by server count each)**

| Field | Key | Source query |
|---|---|---|
| `geo` | country ISO code (`VARCHAR(2)`); unknown-country rows excluded | `SELECT country, COUNT(*) FROM tracker_servers WHERE country IS NOT NULL GROUP BY country ORDER BY COUNT(*) DESC LIMIT 10` |
| `platform` | server-software string from version name (`Paper`, `Spigot`, `Forge`, plain version → `unknown`), **keys lowercased** so one canonical bucket per software; `Platform` enum not involved (edition vs software) | `SELECT LOWER(COALESCE(platform, 'unknown')), COUNT(*) FROM tracker_servers GROUP BY 1 ORDER BY COUNT(*) DESC LIMIT 10` |
| `protocol` | status-protocol number (JSON keys render as strings); unknown excluded | `SELECT protocol, COUNT(*) FROM tracker_servers WHERE protocol IS NOT NULL GROUP BY protocol ORDER BY COUNT(*) DESC LIMIT 10` |

Repo constraints: no `pom.xml` changes; strict Controller → Service → Repository layering;
record DTOs; explicit `{}` braces.

---

## 2. Endpoints

### Now — stats only

```
GET /servers/tracker/stats
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

Controller: new `controller/TrackerController` — `@RestController`, `@RequestMapping("/servers/tracker")`, `@Tag(name = "Server Tracker Controller")`, under the existing `/servers` base path.

### Roadmap (not built now)

- `GET /servers/tracker` — paginated tracked-server list (uuid, ip, port, version, online, last_updated).
- `GET /servers/tracker/{uuid}` — single-server detail + latest online-history point.
- `GET /servers/tracker/{uuid}/history` — `tracker_server_online_history` series.
- `GET /servers/tracker/players/{uuid}` — reverse `tracker_player_history` lookup: servers a player was seen on, with first/last seen.
- `GET /servers/tracker/stats` remains backward-compatible through all of these (fields may only grow).

---

## 3. Service design — `TrackerStatsService`

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
| Repository | `repository/postgres/TrackedServerRepository.java` | `findByIpAndPort` (store reuse) + `@Query` count/sum/group-by projections (`interface Breakdown { String getKey(); long getCount(); }`) |
| Repository | `repository/postgres/PlayerHistoryRepository.java` | `@Query` distinct-player count |
| Metric | `ServerTrackerMetric` | `tracker_servers` gauge fed by the same counters (`docs/SERVER_TRACKER_PLAN.md` §10) |

---

## 4. Config

```yaml
mc-utils.server-tracker.stats.refresh-seconds: 300   # stats recompute cadence
```

Everything needed for the tables lives under `mc-utils.server-tracker.*` (see
`docs/SERVER_TRACKER_PLAN.md` §9).

---

## 5. Tests & rollout

- `TrackerStatsServiceTest` (mocked repositories): initial load sets all counters and
  breakdowns; scheduled refresh replaces the snapshot; overlapping refresh skipped; requests
  served from memory without DB access; disabled-tracking state.
- `TrackerControllerTest`: response shape (three counters + three maps), `Cache-Control:
  max-age=60` header, disabled-tracking empties.
- The project has no DB integration harness (unit tests only) — the SQL (counters + group-bys)
  is verified against the real Postgres schema in the staged rollout: run against a scoped
  `include-cidrs` campaign + `psql` sanity check of the exact queries above.
- Swagger: endpoint appears automatically via `OpenAPIConfiguration`.

---

## 6. Execution order

1. `TrackerStatsResponse` + `TrackerController` skeleton (constants only).
2. `TrackedServerRepository` / `PlayerHistoryRepository` query methods (require
   `docs/SERVER_TRACKER_PLAN.md` §4 entities).
3. `TrackerStatsService` (async load, scheduler, cycle hook, breakdown projections).
4. Wire chunk-completion → refresh; metrics gauge.
5. Unit tests + staged rollout (scoped scan → verify counters/breakdowns → unlock).