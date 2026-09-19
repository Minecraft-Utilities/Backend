# Server Tracker Plan

Status: **implemented** (2026-09-20) — the internet **server tracker** is one feature: IPv4
discovery sweep + per-server telemetry + equal-cadence refresh + public API. Rename of the v1
scanner → tracker shipped (package `service.tracker`, `mc-utils.server-tracker.*` config,
`server_tracker_*` metrics, `tracker_progress`, dashboard updated), `V43__server_tracker.sql`
applied, and every section below is live code. `mvn test` green (56 tests). End-to-end smoke
verified against a real Postgres/Redis + a live status-protocol server: refresh claim →
ping → store upserts (server telemetry/player history/online history), `times_seen`
increments, offline counting, and the stats endpoint + cache header. Public API surface:
`docs/SERVER_TRACKER_API_PLAN.md`.

---

## 1. Goal

One service, one namespace, one dataset. The tracker:
- sweeps the public IPv4 space (discovery) to find live Java Minecraft servers,
- harvests online-player `(uuid, name)` samples into the existing player submit queue,
- persists rich per-server telemetry — **geo, platform, protocol**, counts, version, MOTD and
  favicon fingerprints, latency, secure-chat flags —
- keeps every tracked server current with a continuous **refresh cycle where all servers are
  equal** (no velocity, no priority tiers, no scheduled timestamps),
- exposes the dataset over the public API (stats first).

The v1 scanner mindset ("servers as a means to harvest player names") is replaced by the tracker
mindset: **the servers themselves are the dataset**, consumers of it (stats endpoint, later
server lists/history) come first.

Constraints: no `pom.xml` changes; strict Controller → Service → Repository layering; explicit
`{}` braces; Java 25 features where natural.

---

## 2. Architecture — one service, two loops

```
┌────────────────────────────── ServerTrackerService ──────────────────────────────┐
│                                                                                   │
│  Discovery loop ──► ServerTrackerDiscovery ──► ServerTrackerVerifier ──┐          │
│  (IP space, NIO     (TCP connect probes,      (status ping + port      │          │
│   probes, resumable) ports list)               walk + honeypot gate)   ▼          │
│                                                                  ServerTrackerStore│
│  Refresh cycle ──► ServerTrackerRefresher ────────────────────────► (upserts,     │
│  (last_refreshed    (token pings, shared                         player history,  │
│   cycle, min-gap)    verify pool)                                geo, online ts)  │
│                                                                        │ stats hook│
└────────────────────────────────────────────────────────────────────────▼──────────┘
                                                           TrackerStatsService ──► API
```

Components (old → new):

| Old (scanner) | New (tracker) | Note |
|---|---|---|
| `service.scanner` package | `service.tracker` | move + rename |
| `ServerScannerService` | `ServerTrackerService` | orchestrator; owns both loops + lifecycle |
| `ServerDiscoveryScanner` | `ServerTrackerDiscovery` | unchanged logic |
| `ServerScanVerifier` | `ServerTrackerVerifier` | + `ServerSnapshot` sink (§6) |
| `ServerScannerMetric` | `ServerTrackerMetric` | absorbs refresh + tracked metrics (§10) |
| `ScanProgressRow` / `scan_progress` | `TrackerProgressRow` / `tracker_progress` | renamed in migration |
| `ScannedServerRow` / `scanned_servers` | `TrackedServerRow` / `tracker_servers` | full telemetry row (§4) |
| `ServerScannerRepository` | `ServerTrackerRepository` | + `PlayerHistoryRepository`, `TrackerProgressRepository` |
| config `mc-utils.server-scanner.*` | `mc-utils.server-tracker.*` | §9 |
| log prefix "Server scanner" | "Server tracker" | |
| — | `ServerTrackerRefresher` | refresh cycle (§5) |
| — | `ServerTrackerStore` | persistence + geo (§6) |
| — | `TrackerController` / `TrackerStatsService` | API (`docs/SERVER_TRACKER_API_PLAN.md`) |

**Unchanged internals:** `Ipv4Space`, `HoneypotDetector`, `ScanFingerprintStore` /
`RedisScanFingerprintStore`, `BufferedHarvester` — generic enough to keep
their names; the rename targets the public surface (service, verifier, metric, config, tables).
(HoneypotSurgeGuard was removed entirely: honeypots cause no pauses, the flagged host's IP is
skipped.)

**Merging the loops:** both the discovery campaign and the refresh cycle run inside
`ServerTrackerService` as independent daemon loops started at `ApplicationReadyEvent` and
stopped at `ContextClosedEvent`, sharing the verify worker pool, the store and the metrics.
Discovery feeds new rows; the refresh cycle keeps everything current in between.

---

## 3. Discovery (the renamed scanner)

Carried over from the implemented v1, renamed:

- `Ipv4Space` public-space iterator (RFC 6890 + DoD exclusions, seeded /24 randomization,
  resumable via `tracker_progress` singleton row).
- `ServerTrackerDiscovery` — NIO TCP-connect probes over the configurable port list, bounded
  in-flight, pace distributed across /16s; single pass ≈ 8–9 days at 5k probes/s (tunable).
- `ServerTrackerVerifier` — status-ping verification, port walk (+10 beyond the last working
  port per IP, probe cap), harvest.
- `HoneypotDetector` layers (structural v4-UUID/name checks, sample consistency, farm
  fingerprinting, per-IP repetition) unchanged; final gate stays the Mojang verification in
  `PlayerSubmitService` — the single player intake point (attributed to ImFascinated via
  `SUBMITTED_BY`).
- The port-exploration rule is unchanged: walk only from a verified base port, sliding window.

**Rename phase (mechanical, do first):** move package (`service.scanner` → `service.tracker`,
`metric.impl.scanner` → `metric.impl.tracker`), rename classes/config/metrics/log messages,
**flip the feature-flag `@Value` defaults** (`enabled`, `tracking.enabled`, `refresh.enabled`)
from `false` to `true` per §9, rename tests (`ServerScannerServiceTest` →
`ServerTrackerServiceTest`, `ServerScanVerifierTest` → `ServerTrackerVerifierTest`,
`ScannerIntegrationTest` → `TrackerIntegrationTest`), update the Grafana dashboard
(`dashboard.json`) metric names (`server_scanner_*` → `server_tracker_*`) — no behavior change
beyond the defaults; run `mvn test` to prove equivalence before layering v2 on top.

---

## 4. Telemetry schema — `V43__server_tracker.sql`

Creates the tracked dataset, migrates `scanned_servers` rows, renames `scan_progress`:

```sql
ALTER TABLE scan_progress RENAME TO tracker_progress;

CREATE TABLE tracker_servers (
    uuid                UUID        PRIMARY KEY,          -- surrogate id; protocol advertises none
    ip                  VARCHAR(45) NOT NULL,
    port                INTEGER     NOT NULL,
    first_seen          TIMESTAMPTZ NOT NULL,
    last_updated        TIMESTAMPTZ NOT NULL,             -- last SUCCESSFUL status fetch
    online_count        INTEGER     NOT NULL DEFAULT 0,
    max_players         INTEGER     NOT NULL DEFAULT 0,
    version             VARCHAR(64),                      -- e.g. "Paper 1.21.4"
    protocol            INTEGER,                          -- status-protocol number
    platform            VARCHAR(32),                      -- Paper/Spigot/Forge/Fabric/Vanilla...
    motd                VARCHAR(1024),                    -- cleaned text
    motd_hash           BYTEA,                            -- SHA-256; network fingerprinting
    favicon_hash        BYTEA,                            -- SHA-256; network fingerprinting
    modded              BOOLEAN     NOT NULL DEFAULT FALSE,
    latency_ms          INTEGER,
    prevents_chat_reports BOOLEAN  NOT NULL DEFAULT FALSE,
    enforces_secure_chat  BOOLEAN  NOT NULL DEFAULT FALSE,
    previews_chat         BOOLEAN  NOT NULL DEFAULT FALSE,
    country             VARCHAR(2),                       -- MaxMind ISO code
    asn                 BIGINT,                           -- MaxMind AS number
    sample_count        INTEGER     NOT NULL DEFAULT 0,
    honeypot            BOOLEAN     NOT NULL DEFAULT FALSE,
    consecutive_offline INTEGER     NOT NULL DEFAULT 0,   -- 0 = alive; stats/metrics only
    last_refreshed      TIMESTAMPTZ NOT NULL DEFAULT TIMESTAMPTZ 'epoch', -- refresh cycle; §5
    UNIQUE (ip, port)
);

-- Refresh cycle: least-recently-refreshed first; every server equal (see §5)
CREATE INDEX idx_tracked_servers_refresh ON tracker_servers (last_refreshed);

CREATE TABLE tracker_player_history (
    server_uuid UUID        NOT NULL REFERENCES tracker_servers(uuid) ON DELETE CASCADE,
    player_uuid UUID        NOT NULL,                     -- sample always carries uuid+name
    username    VARCHAR(16) NOT NULL,                     -- updated on rename (uuid stays key)
    first_seen  TIMESTAMPTZ NOT NULL,
    last_seen   TIMESTAMPTZ NOT NULL,
    times_seen  INTEGER     NOT NULL DEFAULT 1,
    PRIMARY KEY (server_uuid, player_uuid)
);
CREATE INDEX idx_player_history_player ON tracker_player_history (player_uuid);
CREATE INDEX idx_player_history_username ON tracker_player_history (username);

CREATE TABLE tracker_server_online_history (                       -- popularity/uptime time series
    server_uuid UUID        NOT NULL REFERENCES tracker_servers(uuid) ON DELETE CASCADE,
    sampled_at  TIMESTAMPTZ NOT NULL,
    online      INTEGER     NOT NULL,
    max         INTEGER     NOT NULL,
    version     VARCHAR(64),
    PRIMARY KEY (server_uuid, sampled_at)
);
-- optional later: tracked_server_mods (server_uuid, mod_id, version) from ForgeData.mods

-- Backfill old scanner rows into tracker_servers, then drop the old table
INSERT INTO tracker_servers (uuid, ip, port, first_seen, last_updated, sample_count, honeypot)
SELECT gen_random_uuid(), ip, port, first_seen, last_seen, sample_count, honeypot
FROM scanned_servers;
DROP TABLE scanned_servers;
```

Notes:
- `uuid` is our generated id (v4 on first sight) — Java status protocol has no server UUID field.
- Two timestamps with different meanings: `last_updated` = last **successful** status fetch
  (data staleness, freezes when a server dies; the originally requested column) and
  `last_refreshed` = last refresh **attempt** (cycle order, bumps on every claim; see §5).
- `tracker_player_history` is keyed by `player_uuid` (not username): usernames rename, UUIDs are stable;
  the username column is kept and updated on rename.
- Backfilled rows start with `online_count = 0` and NULL `version`/`protocol`/`platform`/
  `country`/`asn` (not known historically); `last_refreshed = epoch` makes them refreshable
  immediately — they converge on the first cycles.
- Only post-honeypot-verdict players are written (same list the harvester gets), and only
  after the sample-identity gate: each (name, uuid) pair must match the players table — the
  Mojang-verified identity store, consulted cache-only via `PlayerService.getCachedPlayer` —
  else the entry is dropped (`fake_identity`) or ignored when unknown (`unverified`). No
  Mojang lookups here; the submit pipeline owns the only profile lookup.
- `motd` is stored truncated to 1024 chars (cleaned text); `motd_hash`/`favicon_hash` are
  SHA-256 of the raw canonical payloads (32-byte `BYTEA`).
- Store write semantics are upserts, not inserts: `tracker_servers` conflicts on `(ip, port)` —
  first_seen preserved, all other fields overwritten; `tracker_server_online_history` conflicts on
  `(server_uuid, sampled_at)` — last write in a flush window wins (two refreshes of the same
  server can land in one flush); `tracker_player_history` conflicts on the PK — first_seen preserved,
  username/last_seen/times_seen updated (`times_seen++`).

---

## 5. Refresh cycle (`ServerTrackerRefresher`)

Every tracked server is **equal**: no velocity, no priority tiers, no scheduled
`next_refresh_at`. The cycle continuously refreshes the **least-recently-refreshed** servers
first — one timestamp per row (`last_refreshed`), no future-dated bookkeeping anywhere.

**Claim** (transactional, Postgres-native):

```sql
SELECT uuid, ip, port FROM tracker_servers
WHERE last_refreshed < now() - make_interval(hours => :minGapHours)
ORDER BY last_refreshed ASC
LIMIT :chunkSize
FOR UPDATE SKIP LOCKED
```

then bump the claimed rows (`last_refreshed = now()`) in the same transaction. The claim-time
bump is the entire concurrency story:
- claimed rows jump to the back of the line, so a slow or timed-out chunk causes neither
  re-claims nor hammering — no lease column, no lease constants;
- `FOR UPDATE SKIP LOCKED` keeps overlapping instances/restarts from double-claiming;
- a crash mid-chunk costs nothing: the rows were already bumped and come around again after
  the rest of the set — equal treatment, nothing to leak.

**Cadence**: one config, one filter — `refresh.min-gap-hours` (default 6): a server is claimed
only when it has not been refreshed for at least the gap. The cycle keeps spinning; when every
server is fresher than the gap (or the set is empty) it idles (sleep ~10s) until rows age out
or discovery adds new ones (inserts default `last_refreshed = epoch`, so they are refreshable
on the very next cycle; legacy rows converge the same way). No per-row scheduling of any kind.

**Chunk processing** (mirrors `PlayerRefreshService.refreshChunk`): fan-out
`CompletableFuture.runAsync` on a fixed pool with a semaphore-bounded inflight cap,
`orTimeout(CHUNK_TIMEOUT).join()` (CHUNK_TIMEOUT = 10 min, mirroring the player system; per-ping
socket timeout = `verify.timeout-ms`), per-server outcomes counted. Per server, token-only ping
(no DNS, no Redis, no geo re-lookup). **Anti-poisoning parity:** every refresh sample runs the
same `HoneypotDetector` evaluate as discovery; the verdict gates both `tracker_player_history` writes
and queue harvest identically — a server newly flagged as honeypot on a refresh gets flagged in
the store and its sample dropped. Honeypot-flagged hosts are skipped entirely by the verifier:
no port walk, nothing harvested from the IP (it's just a honeypot — no harvest pauses):
- **success** → `ServerTrackerStore` upsert (server row + players + history row), reset
  `consecutive_offline`; `last_updated` advances (data freshness), `last_refreshed` already
  advanced at claim (attempt time);
- **failure** → `consecutive_offline++`; the server rotates back after the gap like everyone
  else — dead servers cost the same single probe per pass. The counter exists for
  stats/metrics only: "alive" = `consecutive_offline = 0`, and the stats endpoint's
  `onlinePlayers` excludes dead and honeypot servers;
- honeypot servers refresh counts/version only, never samples.

**Stats hook**: each finished chunk signals `TrackerStatsService` to refresh
(`docs/SERVER_TRACKER_API_PLAN.md` §3) — the API counters move with refresh activity.

Volume: with the default 6h gap, ~400k refreshes per day at 200 concurrent pings ≈ 1–2 h of
worker time spread across the day — negligible vs. the discovery campaign.

---

## 6. Verify → store pipeline (merge point)

- `ServerTrackerVerifier.ScannedServerSink` (renamed `ServerTrackerSink`)
  `.recordServer(ip, port, sampleCount, honeypot)` becomes `recordServer(ServerSnapshot)` where
  `ServerSnapshot` is a record carrying ip, port,
  online/max, version name+protocol+platform, motd hash, favicon hash, latency, secure-chat
  flags, verdict players, honeypot flag. `handleServer` already has the full token in scope —
  no extra pings; latency = nanoTime around the existing `ping()` call.
- `persistScannedServer` moves into `ServerTrackerStore` (Spring service): upsert server row +
  one batched `INSERT ... ON CONFLICT` for tracker_player_history (native multi-row statement via
  JdbcTemplate — **no pom.xml changes**) + one `tracker_server_online_history` row per sighting.
- **Geo enrichment** — `MaxMindService.lookupIp(ip)` (`@Cacheable "geoLookup"`, returns
  `IpLookup` with `GeoLocation` + `AsnLookup`): once per **new** server (first sight, inside
  the store flush, not on refresh), gated by `tracking.geo.enabled`. `platform`/`protocol`
  come from the token's `JavaVersion` (`detailedCopy()` derives the software string; distinct
  from the `Platform` `JAVA`/`BEDROCK` edition enum).
- Buffered flush (interval/batch, same pattern as `BufferedHarvester`) so the verify pool never
  blocks on DB. Inserts default `last_refreshed = epoch`, so the refresh cycle picks new
  servers up on its next spin; refresh claims bump `last_refreshed` through the same store.
- **Discovery-upsert semantics** (re-sighting): a discovery success updates the same fields as
  a refresh success — it IS a successful status fetch — so it advances `last_updated` and
  resets `consecutive_offline`, but leaves `last_refreshed` untouched: refresh cadence is
  owned exclusively by the refresh cycle (a re-sighted dead server flips alive immediately;
  whether it gets re-pinged soon after is the cycle's call, and the min-gap filter prevents
  wasted double pings right after discovery).
- **Geo failure is non-fatal:** `lookupIp` may throw or return empty when the MaxMind
  databases are absent (license unset) — the store catches it, leaves `country`/`asn` NULL and
  records a metric; a geo hiccup never fails a server flush.
- `ScannedServerRow`/`ServerScannerRepository.findByIpAndPort` path is replaced by the store.

---

## 7. Retention janitor

`tracker_server_online_history` grows ~400k rows/day at the 6h cadence → scheduled prune: raw rows
kept 30 days, downsampled to hourly for 90, then deleted. `tracker_player_history` is bounded by
distinct (server, player) pairs and needs no janitor.

---

## 8. Cool extras (tiered)

**Tier 1 — already in the token, build now:** max_players, platform/protocol/version (version
share + modded stats), motd/favicon hashes (network fingerprinting; future re-identification
when a server changes IP), latency, secure-chat flags (public "chat-report-safe" dataset),
country+ASN (server distribution stats).

**Tier 2 — later, cheap:** mod lists (`tracked_server_mods`) → most common mods on public
servers; version drift detection (from `tracker_server_online_history.version`); player networks
(players seen on ≥2 servers ⇒ networked hosts, one query over `tracker_player_history`); uptime stats.

**Out of scope:** Bedrock probing, per-player session tracking (status protocol samples only).

---

## 9. Configuration — unified `mc-utils.server-tracker.*`

All features **default to enabled** — the tracker starts at boot; operators opt out per
feature:

```yaml
mc-utils.server-tracker.enabled: true                      # discovery campaign
mc-utils.server-tracker.discovery.concurrency: 20000
mc-utils.server-tracker.discovery.connect-timeout-ms: 1000
mc-utils.server-tracker.ports.discovery: 25564,25565,25566,25567
mc-utils.server-tracker.ports.window: 10
mc-utils.server-tracker.ports.max: 65535
mc-utils.server-tracker.ports.probe-cap-per-ip: 300
mc-utils.server-tracker.verify.concurrency: 200
mc-utils.server-tracker.verify.timeout-ms: 5000
mc-utils.server-tracker.enqueue.batch-size: 1000
mc-utils.server-tracker.enqueue.rate-per-minute: 3000
mc-utils.server-tracker.honeypot.max-distinct-nets-per-fingerprint: 50
mc-utils.server-tracker.honeypot.window-hours: 24
mc-utils.server-tracker.honeypot.max-identical-port-samples: 3
mc-utils.server-tracker.sample-verify.enabled: true        # require (name,uuid) to match the players table (cache-only)
mc-utils.server-tracker.progress-log-interval-seconds: 60
mc-utils.server-tracker.ip.exclude-extra-cidrs: ""
mc-utils.server-tracker.ip.include-cidrs: ""
mc-utils.server-tracker.tracking.enabled: true             # telemetry persistence
mc-utils.server-tracker.tracking.geo.enabled: true         # MaxMind lookup per new server
mc-utils.server-tracker.refresh.enabled: true              # refresh cycle
mc-utils.server-tracker.refresh.min-gap-hours: 6           # min hours between refreshes
mc-utils.server-tracker.refresh.chunk-size: 2500           # mirrors player-refresh
mc-utils.server-tracker.refresh.concurrent-fetches: 200    # mirrors player-refresh
mc-utils.server-tracker.refresh.timeout-ms: 5000
mc-utils.server-tracker.history.raw-retention-days: 30
mc-utils.server-tracker.history.hourly-retention-days: 90
mc-utils.server-tracker.stats.refresh-seconds: 300         # API stats recompute (see API plan §4)
```

**Feature-flag interplay (explicit):**
- `enabled` (discovery): when false, no IP-space probing. The refresh cycle and stats still
  work standalone over already-tracked rows (e.g. after a previous campaign).
- `tracking.enabled` gates **all store writes** (server rows, player history, online history).
  When false: discovery still probes/verifies/harvests players exactly like v1, but persists
  nothing (progress only), and the refresh cycle idles on an empty table.
- `refresh.enabled` gates only the refresh loop; discovery updates still land in the store
  when tracking is on.
- `geo.enabled` is independent of the others; false or missing MaxMind DB → `country`/`asn`
  stay NULL (never fatal).

**Config-key migration hazard:** the rename silently orphans old `mc-utils.server-scanner.*`
env vars — deployment env must be migrated to `mc-utils.server-tracker.*` in the same change
(the renamed `@Value`s read the new keys; with defaults flipped to enabled, forgetting the
migration would apply default rates instead of tuned ones — verify with
`/actuator/env` after deploy).

---

## 10. Metrics — `ServerTrackerMetric`

One metric class (absorbed `ServerRefreshMetric`). Existing v1 metrics are renamed with their
`_total` suffix convention preserved (`server_scanner_*` → `server_tracker_*`); the
v1-planned `batch_not_found_rate` gauge was never implemented (the NOT_FOUND gate lives inside
the submit pipeline) and is omitted:

- discovery (renamed): `ip_probes_total`, `connect_open_total`, `servers_verified_total`,
  `ports_probed_walk_total`, `players_harvested_total`, `players_enqueued_total`,
  `tracker_progress_24s` gauge;
- anti-honeypot (renamed): `sample_entries_dropped_total{reason}` (incl. `fake_identity`,
  `unverified` from the sample-identity gate), `honeypot_servers_flagged_total`,
  `honeypot_fingerprints_blocked_total`;
- tracked dataset (new): `tracker_servers` gauge — **single-sourced from the
  `TrackerStatsService` snapshot** (never a second counter in the store/refresh paths),
  `players_seen_first_total`, `geo_lookup_failures_total`;
- refresh (new): `refresh_pings_total`, `refresh_persisted_total`,
  `refresh_failed_total{reason}`;
- janitor (new): `history_rows_pruned_total`.

---

## 11. Tests

Project has no DB test harness — unit tests with mocks/embedded fakes; the SQL is verified
against real Postgres in the staged rollout.

- Rename phase: existing scanner tests keep passing unchanged (imports/names only);
- store upsert semantics (first_seen preserved, last_updated bumped, username rename, platform/
  protocol/geo columns populated once, motd truncation, `times_seen` increments);
- discovery-vs-refresh interplay (discovery re-sight advances `last_updated` + resets
  `consecutive_offline` but leaves `last_refreshed` alone; refresh success resets offline;
  honeypot verdict parity between both paths);
- buffered store flush (accumulates snapshots, emits one multi-row statement per table,
  flush never blocks the verify pool);
- geo failure → NULL rows + metric, flush unaffected;
- refresh claim/cycle (least-recently-refreshed order, min-gap filter, claim bumps
  `last_refreshed` → no re-claim/hammering, `SKIP LOCKED` overlap safety, resume from the
  column ordering);
- dead-server handling (`consecutive_offline` increments, same gap rotates everyone back,
  `last_updated` freezes while `last_refreshed` advances);
- janitor retention math;
- `TrackerIntegrationTest` (existing `ScannerIntegrationTest`, renamed, reusing
  `FakeMinecraftServer`) extended to assert version/online/max/platform land in
  `tracker_servers` and player history rows are written exactly for post-verdict players.

---

## 12. Execution order

1. **Rename phase:** package `service.scanner` → `service.tracker`, class/config/metric/log
   renames per §2, `scan_progress` → `tracker_progress` via migration; **migrate deployment
   env vars to `mc-utils.server-tracker.*` and update `dashboard.json` metric names** (see §9
   hazard note); `mvn test` green (behavior unchanged).
2. `V43__server_tracker.sql` (tracker_servers + tracker_player_history + tracker_server_online_history +
   backfill + drop + rename) + entities/repos.
3. `ServerSnapshot` + verifier sink refactor + latency capture; update existing tests.
4. `ServerTrackerStore` (batched upserts, buffered flush, geo enrichment) wired into
   `ServerTrackerService`.
5. `ServerTrackerRefresher` (continuous claim cycle: min-gap filter, claim bump, `SKIP LOCKED`,
   chunk loop, dead-server counting) + lifecycle + config.
6. Retention janitor, metrics.
7. Stats endpoint (see `docs/SERVER_TRACKER_API_PLAN.md`) + integration test + staged rollout
   via `include-cidrs` scoped scan, then refresh on real data.