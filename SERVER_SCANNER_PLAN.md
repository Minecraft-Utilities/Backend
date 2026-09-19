# Server Scanner Implementation Plan

Full public IPv4 sweep for Java Minecraft servers: discover hosts, harvest online-player usernames from status-ping samples, and feed them into the existing player submit queue. Java servers only. Anti-honeypot hardening built in. Port-exploration rule: sequential walk, +10 beyond the last working port per IP to extract the maximum number of servers.

Status: **implemented** — `mvn test` green (38 tests, clean build). See `xyz.mcutils.backend.service.scanner` for code, `V42__server_scanner.sql` for persistence, `src/test/java/xyz/mcutils/backend/service/scanner` for tests. Opt-in via `mc-utils.server-scanner.enabled=true`.

---

## 1. Goals & constraints

**Spec**

- Sweep the entire public IPv4 space (private/government/reserved ranges excluded).
- Find live Java Minecraft servers via their status protocol.
- Harvest player `(uuid, name)` pairs from each server's `players.sample` and add them to the player submit queue.
- Anti-honeypot detection must be built in (fake/cracked samples, honeypot farms).
- Port rule: probe the base port `25565` first. Only if a **valid server is verified** there, walk ports incrementally, extending the probe window **+10 beyond the last working port** on that IP (sliding window), to collect every server on multi-server hosts. If nothing verifies at the base port, move to the next IP immediately.
- Java servers only (no Bedrock).

**Repo constraints**

- No `pom.xml` changes (all JDK + existing Redis/Postgres).
- Strict layered architecture; DTO/record boundaries; explicit `{}` braces.
- `PlayerSubmitService` stays the single player intake point so existing Mojang verification acts as the final anti-poisoning gate.
- New code in `xyz.mcutils.backend.service.scanner`; new metric in `xyz.mcutils.backend.metric.impl.scanner`.

---

## 2. Architecture

```
┌─────────────┐   /24 order    ┌──────────────────┐  connect ok  ┌──────────────────────────┐
│ Ipv4Space   │───────────────▶│ Discovery Stage  │─────────────▶│ Verification + Port Walk │
│ (iterator,  │  randomized,   │ (NIO selector,   │              │ (pingToken per host,     │
│  exclusions)│  resumable     │  TCP connect     │              │  harvest, +10 walk)      │
└─────────────┘                │  probes :25565)  │              └──────────┬───────────────┘
                              └──────────────────┘                         │ sample
                                                                    ┌───────▼────────┐
                                                                    │ HoneypotDetector│
                                                                    └───────┬────────┘
                                                                            │ clean (uuid,name)
                                                              ┌─────────────▼─────────────┐
                                                              │ PlayerSubmitService        │
                                                              │ .submitPlayers(uuids, sid) │  (dedupe → Redis queue →
                                                              └───────────────────────────┘   Mojang verify → persist)
```

Components

- `service/scanner/Ipv4Space.java` — public-space iterator (ranges, exclusions, randomized resumable /24 order).
- `service/scanner/ServerDiscoveryScanner.java` — async NIO TCP-connect sweep of `:25565`.
- `service/scanner/ServerScanVerifier.java` — status-ping verification, port walk, harvest.
- `service/scanner/HoneypotDetector.java` — layered anti-honeypot filters.
- `service/scanner/ServerScannerService.java` — orchestrator + lifecycle + metrics.
- `metric/impl/scanner/ServerScannerMetric.java` — counters/gauges.
- `repository/postgres/ServerScannerRepository.java` + `V42__server_scanner.sql` — progress + found-server persistence.
- Refactor: `JavaMinecraftServerPinger.pingToken(...)` (token-level ping; `ping()` delegates).

---

## 3. IP space model — `Ipv4Space`

Deterministic, resumable iterator over /16 prefixes and their /24 blocks.

**Excluded ranges (RFC 6890 special-purpose + DoD "government" allocations + host self)**

`0/8`, `10/8`, `100.64/10` (CGNAT), `127/8`, `169.254/16`, `172.16/12`, `192.0.0/24`, `192.0.2/24`, `192.88.99/24`, `192.168/16`, `198.18/15`, `198.51.100/24`, `203.0.113/24`, `224/4` (multicast), `240/4` (reserved); DoD: `6/8`, `11/8`, `21/8`, `22/8`, `26/8`, `30/8`, `55/8`, `214/8`, `215/8`. Plus configurable `exclude.extra-cidrs` and the scanner host's own egress IP.

Remaining ≈ **3.6B addresses / ~16.5M /24s**.

**Randomization & resume**

- Shuffle /24s *within* each /16 with a seeded PRNG (seed derived from root seed + /16); iterate /16s in seeded-shuffled order. Avoids burst patterns (IDS/abuse alarms) and never materializes the full permutation.
- Persist progress `(root_seed, current /16 in permuted order, offset within /16)` in a singleton `scan_progress` row → crash/restart resumes without re-hashing the space.

---

## 4. Discovery stage — `ServerDiscoveryScanner`

- Pure **TCP connect probe** on the base port `25565` via `java.nio` non-blocking channels + one selector thread; `finishConnect()` success → verification, failure/timeout → advance. No data sent in this stage (cheap: <0.1% of the space is even open).
- Bounded in-flight cap (default 5,000 sockets), connect timeout ~2s, one probe per IP, no retries.
- Pace distributed across /16s.
- Single pass ≈ 8–9 days at 5k probes/s; concurrency is the campaign-length tunable.

**Discovery invariants**

- Only the base port is probed in discovery. The port walk happens in verification, and only on hosts whose base port already verified.

---

## 5. Verification + port walk — `ServerScanVerifier`

**Refactor first:** extract `JavaMinecraftServerPinger.pingToken(host, ip, port, EMPTY_DNS, timeout)` returning the raw `JavaServerStatusToken`; `ping()` delegates. The scanner uses only the token path — `JavaMinecraftServer.create()` runs MOTD component serialization, favicon base64 decode, and name color-processing, all needless at scan scale.

**Verify** with a bounded pool (default 100) against the raw IP (no DNS, no `ServerService`, no Redis cache pollution, no MaxMind). Success = token parses with `version` + `players` payload → a real Java server:
- record in `scanned_servers` (ip, port, first_seen, last_seen, sample count, honeypot flag);
- harvest sample → `HoneypotDetector` → enqueue clean players via `PlayerSubmitService`.

### Port walk (+10 from last working port) — per IP, base port `25565`

```
lastWorking = basePort                       // 25565
if !verify(basePort): next IP                // no server → no walk, per spec
port = basePort + 1
probes = 0
while port <= min(lastWorking + WINDOW, MAX_PORT) and probes < PROBE_CAP_PER_IP:
    if verify(port): lastWorking = port      // only a handshake-verified Java server re-anchors
    port++
    probes++
```

Properties

- **Sliding window:** the ceiling is always `lastWorking + 10`, recomputed after every verified server. A host running servers on `25565, 25566, 25571, 25580` gets all four: dead ports in between never truncate the search; each verified port pushes the window 10 higher.
- **Only Java-verified servers re-anchor** — RCON (`25575`), web servers, or other services squatting MC-adjacent ports consume a probe but never extend the window.
- **Defensive caps (config-driven):**
  - `WINDOW = 10` (ports beyond last working port that may still be probed),
  - `MAX_PORT = 65535`,
  - `PROBE_CAP_PER_IP` (default 200) — protects against pathological hosts running enormous port ranges, bounds per-IP time and outbound traffic.
- Walk is sequential per IP (protocol politeness, no connection burst to one host), but many verified hosts are walked concurrently by the verify pool. Per-IP cost is bounded by `PROBE_CAP_PER_IP`; the number of hosts entering the walk is small (only hosts with a working base port), so total walk traffic is negligible vs. discovery.
- Escalated ports do **not** get a second, deeper walk — the window is shared across the whole IP: `lastWorking` starts at the base port and the walk covers `basePort+1 .. lastWorking+WINDOW` for the lifetime of that host's scan.

---

## 6. Anti-honeypot — `HoneypotDetector` (layered, cheap → expensive)

1. **Structural (always):** sample UUID must parse and be **version 4** (`UUIDUtils.isOnlineMode`, new helper — nibble must be `4`). Offline/cracked servers emit v3 MD5 UUIDs; honeypot farms emit garbage/nil UUIDs. Drop entry; metric `sample_entries_dropped{reason=non_v4}`.
2. **Name sanity:** strip `§` codes; require `^[A-Za-z0-9_]{1,16}$`; drop entries failing or duplicated within the sample.
3. **Sample consistency:** drop the server's harvest when `sample.length > online`, `online > max`, or `online <= 0` with non-empty sample.
4. **Farm fingerprinting:** canonical hash of the ordered sample UUID set; Redis set per fingerprint (TTL window, default 24h) tracking distinct /24s it appears in. `>= max-distinct-nets` (default 50) → honeypot fingerprint: block future matches (short-TTL blacklist), flag the servers, drop their samples. Also flag a host whose identical sample repeats on ≥ 3 of its verified ports (walk makes this pattern visible).
5. **Final gate (free re-use):** the submit pipeline verifies against Mojang (`fetchProfile` → 404 drops). Track per-batch `NOT_FOUND` rate; above `honeypot.max-batch-not-found-rate` (default 0.30) → pause enqueueing, log + metric alarm. This catches honeypot waves that pass the structural filters.

All thresholds config-driven; every drop reason has a metric.

---

## 7. Queue integration

`playerSubmitService.submitPlayers(uuids, submittedBy)` in batches (default 1000, matching `BATCH_SIZE`), enqueue rate-capped (~3k/min) so the Redis queue cannot outrun the shared 600 rpm Mojang budget (40 concurrent consumes). Existing dedupe (`getExistingPlayerIds`, Redis dedupe keys) makes re-scanned names no-ops.

⚠️ `submitPlayers` requires `submittedBy` to be a **valid UUID** (`UUIDUtils.parseUuid` throws otherwise). Use **ImFascinated's UUID** — `eeab5f8a-18dd-4d58-af78-2b3c4543da48` (resolved from Mojang `users/profiles/minecraft/ImFascinated`, 2026-09-19) — so all scanner harvests are attributed to that account in `TopSubmittedPlayers` / `incrementSubmittedUuids`. A UUID keeps working even if the account renames. No queue-format change; legacy Redis entries stay parseable.

Constant to define in `ServerScannerService`: `SUBMITTED_BY = UUID.fromString("eeab5f8a-18dd-4d58-af78-2b3c4543da48")`.

---

## 8. Persistence — migration `V42__server_scanner.sql`

- `scan_progress` (singleton: seed, current /16, /24 offset, state — running/paused/completed).
- `scanned_servers` (ip, port, first_seen, last_seen, sample_count, honeypot_flags) — enables future passes to refresh known hosts instead of re-probing, and optional future server-registry integration.
- Honeypot fingerprint windows live in Redis (TTL-bounded, no table growth).

---

## 9. Config surface (all `@Value`, defaulted off/safe)

```yaml
mc-utils.server-scanner.enabled: false            # opt-in; no scan at boot unless true
mc-utils.server-scanner.discovery.concurrency: 5000
mc-utils.server-scanner.discovery.connect-timeout-ms: 2000
mc-utils.server-scanner.verify.concurrency: 100
mc-utils.server-scanner.verify.timeout-ms: 5000
mc-utils.server-scanner.ports.base: 25565
mc-utils.server-scanner.ports.window: 10          # probe up to lastWorking + window
mc-utils.server-scanner.ports.max: 65535
mc-utils.server-scanner.ports.probe-cap-per-ip: 200
mc-utils.server-scanner.enqueue.batch-size: 1000
mc-utils.server-scanner.enqueue.rate-per-minute: 3000
mc-utils.server-scanner.honeypot.max-distinct-nets-per-fingerprint: 50
mc-utils.server-scanner.honeypot.window-hours: 24
mc-utils.server-scanner.honeypot.max-identical-port-samples: 3
mc-utils.server-scanner.honeypot.surge-window: 100
mc-utils.server-scanner.honeypot.surge-max-flagged: 10
mc-utils.server-scanner.honeypot.surge-cooldown-seconds: 300
mc-utils.server-scanner.ip.exclude-extra-cidrs: ""    # operator override
mc-utils.server-scanner.ip.include-cidrs: ""          # scan only these CIDRs (scoped mode, e.g. 127.0.0.1/32 for CI)
```

---

## 10. Lifecycle, metrics, rollout

- `@EventListener(ApplicationReadyEvent)` starts only when `enabled`; `ContextClosedEvent` stops gracefully (drain in-flight, persist progress). In-app service like `PlayerRefreshService`.
- **Metrics (`ServerScannerMetric`):** `ip_probes`, `connect_open`, `servers_verified`, `ports_probed_walk`, `ports_escalated_verified`, `players_harvested`, `players_enqueued`, `sample_entries_dropped{reason}` (non_v4 / bad_name / mismatch), `honeypot_servers_flagged`, `honeypot_fingerprints_blocked`, `scan_progress` gauge, `batch_not_found_rate` gauge.
- **Rollout gates:** (1) unit tests; (2) integration test against an embedded fake Java status server + fake honeypot on `127.0.0.1` via `include-test-cidrs`; (3) **staged campaign**: scan ~10k random /24s first, audit harvested-name legitimacy and `NOT_FOUND` rate before unlocking the full space; (4) abuse-posture monitoring (below).

---

## 11. Testing & verification

- `Ipv4SpaceTest`: exclusion math (no overlap, ≈3.6B count), iterator determinism, resume-from-offset.
- `UUIDUtilsTest`: online-mode (v4) detection.
- `HoneypotDetectorTest`: cracked-UUID drop, name sanitize, sample-consistency violations, fingerprint threshold crossing with injected clock.
- `PortWalkTest`: sliding-window logic — hosts with gapped server ports (`25565,25566,25571,25580`) all discovered; non-MC open ports don't re-anchor; caps enforced (window, max port, probe cap).
- `ScannerIntegrationTest`: embedded fake Java status server on `127.0.0.1:25565` + alt ports + honeypot twin → assert only the real servers' players reach the queue and honeypots are flagged.
- Smoke: `mvn -q test` (no new deps), then the staged real-world campaign with metric/queue verification.

---

## 12. Operational risk (professional posture)

- **Traffic/abuse:** a full-v4 TCP sweep generates abuse complaints; run from a dedicated host with generic RDNS, keep discovery rate at the polite default, comply with the provider's AUP. The verification stage speaks the Minecraft status protocol — the servers' intended audience (every server-list ping does this).
- **Backpressure:** enqueue cap keeps the Redis queue bounded relative to the shared Mojang limiter; `NOT_FOUND` alarm auto-pauses harvest if a honeypot wave slips through.
- **Cost:** discovery dominates (≈3.6B probes/pass); verification + port walk is bounded (open hosts are rare; per-IP walk capped at `probe-cap-per-ip`). Resume support means restarts never re-scan completed /16s.

---

## 13. Execution order

1. `Ipv4Space` + `UUIDUtils.isOnlineMode` + tests.
2. `pingToken` pinger refactor (`ping()` delegates; existing behavior unchanged).
3. Discovery + verification loop (no harvest yet) + `PortWalkTest`.
4. `HoneypotDetector` + tests.
5. Queue integration (synthetic submitter UUID, batching, rate caps).
6. Metrics, config, persistence (`V42__server_scanner.sql`).
7. Integration test + staged real-world rollout.