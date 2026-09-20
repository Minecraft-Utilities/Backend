package xyz.mcutils.backend.service.tracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.StringUtils;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.tracker.ServerTrackerMetric;
import xyz.mcutils.backend.model.domain.IpLookup;
import xyz.mcutils.backend.model.domain.asn.AsnLookup;
import xyz.mcutils.backend.model.domain.geo.GeoLocation;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;
import xyz.mcutils.backend.repository.postgres.ServerTrackerRepository;
import xyz.mcutils.backend.service.MaxMindService;
import xyz.mcutils.backend.service.MetricService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Persists verified-server snapshots into the tracker dataset: {@code tracker_servers} rows
 * (full telemetry upsert, {@code first_seen} preserved) and {@code tracker_player_history} rows
 * (UUID-keyed upsert: existing rows bump {@code times_seen} + {@code last_seen}, new rows count
 * as first sightings).
 * <p>
 * Buffered like {@code BufferedHarvester}: callers (verify pool, refresh cycle) never block on
 * the database — snapshots accumulate and {@link #flush()} writes them in a few batched,
 * multi-row statements. Every flush upserts the {@code tracker_servers} rows first and keys
 * the player rows by the canonical uuid that upsert returns, so the foreign keys always
 * resolve — brand-new discovery finds and concurrent flushes cannot orphan child rows. Geo
 * enrichment runs once per <b>new</b> server inside the flush, never on refresh.
 * <p>
 * When {@code tracking.enabled} is false every write is a no-op (progress + harvest continue,
 * nothing is persisted).
 */
@Service
@Slf4j
public class ServerTrackerStore {

    private static final int MAX_MOTD_LENGTH = 1024;
    private static final int MAX_VERSION_LENGTH = 64;
    private static final int MAX_PLATFORM_LENGTH = 32;
    private static final int MAX_USERNAME_LENGTH = 16;
    /** Rows per multi-value statement (bounded well under the Postgres parameter limit). */
    private static final int CHUNK_SIZE = 250;
    /** Buffer size that triggers an inline flush (mirrors BufferedHarvester). */
    private static final int FLUSH_THRESHOLD = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ServerTrackerRepository serverTrackerRepository;
    private final MaxMindService maxMindService;
    private final boolean trackingEnabled;
    private final boolean geoEnabled;

    private final List<Pending> buffer = new ArrayList<>();

    public ServerTrackerStore(
            JdbcTemplate jdbcTemplate,
            ServerTrackerRepository serverTrackerRepository,
            MaxMindService maxMindService,
            @Value("${mc-utils.server-tracker.tracking.enabled:true}") boolean trackingEnabled,
            @Value("${mc-utils.server-tracker.tracking.geo.enabled:true}") boolean geoEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.serverTrackerRepository = serverTrackerRepository;
        this.maxMindService = maxMindService;
        this.trackingEnabled = trackingEnabled;
        this.geoEnabled = geoEnabled;
    }

    private record Pending(ServerTrackerVerifier.ServerSnapshot snapshot, Instant seenAt) {}

    private record Resolved(Pending pending, String country, Long asn) {}

    /**
     * Queues a verified-server snapshot for the next flush. Safe to call from any verify/refresh
     * worker; never touches the database (unless the buffer threshold crossed, which mirrors
     * {@code BufferedHarvester}).
     */
    public void record(ServerTrackerVerifier.ServerSnapshot snapshot) {
        if (!trackingEnabled) {
            return;
        }
        List<Pending> overflow;
        synchronized (buffer) {
            buffer.add(new Pending(snapshot, Instant.now()));
            if (buffer.size() < FLUSH_THRESHOLD) {
                return;
            }
            overflow = List.copyOf(buffer);
            buffer.clear();
        }
        flushPending(overflow);
    }

    /**
     * Writes all queued snapshots. Called by the tracker upkeep scheduler (and internally when
     * the buffer threshold is crossed).
     *
     * @return number of snapshots written
     */
    public int flush() {
        List<Pending> batch;
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return 0;
            }
            batch = List.copyOf(buffer);
            buffer.clear();
        }
        return flushPending(batch);
    }

    private int flushPending(List<Pending> batch) {
        try {
            List<Resolved> resolved = new ArrayList<>(batch.size());
            List<Object[]> serverRows = new ArrayList<>();
            ServerTrackerMetric metrics = MetricService.getMetric(ServerTrackerMetric.class);

            for (Pending pending : batch) {
                ServerTrackerVerifier.ServerSnapshot snapshot = pending.snapshot();
                Optional<TrackedServerRow> existing = serverTrackerRepository.findByIpAndPort(snapshot.ip(), snapshot.port());
                UUID serverUuid;
                String country;
                Long asn;
                if (existing.isPresent()) {
                    serverUuid = existing.get().getUuid();
                    country = existing.get().getCountry();
                    asn = existing.get().getAsn();
                } else {
                    serverUuid = UUIDUtils.uuidv7();
                    Geo geo = lookupGeo(snapshot.ip());
                    country = geo.country();
                    asn = geo.asn();
                    if (metrics != null && geo.failed()) {
                        metrics.recordGeoLookupFailure();
                    }
                }
                resolved.add(new Resolved(pending, country, asn));

                String motd = StringUtils.truncate(snapshot.motd(), MAX_MOTD_LENGTH);
                serverRows.add(new Object[]{
                        serverUuid, snapshot.ip(), snapshot.port(),
                        pending.seenAt(), pending.seenAt(),
                        snapshot.online(), snapshot.maxPlayers(),
                        StringUtils.truncate(snapshot.version(), MAX_VERSION_LENGTH), snapshot.protocol(),
                        StringUtils.truncate(snapshot.platform(), MAX_PLATFORM_LENGTH),
                        motd, snapshot.motdHash(), snapshot.faviconHash(),
                        snapshot.modded(), snapshot.latencyMs(),
                        snapshot.preventsChatReports(), snapshot.enforcesSecureChat(), snapshot.previewsChat(),
                        country, asn,
                        snapshot.players().size(), snapshot.honeypot(),
                        0, Instant.EPOCH
                });
            }

// One upsert row per server: PostgreSQL rejects a multi-row ON CONFLICT DO UPDATE
            // whose VALUES rows target the same (ip, port) ("cannot affect row a second time"),
            // and the same server is routinely recorded twice within one flush window
            // (discovery + refresh overlap). The last snapshot in the batch wins the server
            // row, while every pending keeps its own player rows below.
            Map<String, Integer> serverRowIndexByKey = new LinkedHashMap<>();
            List<Object[]> uniqueServerRows = new ArrayList<>();
            int[] pendingToUniqueRow = new int[serverRows.size()];
            for (int i = 0; i < serverRows.size(); i++) {
                Object[] serverRow = serverRows.get(i);
                String key = serverRow[1] + "," + serverRow[2];
                Integer existing = serverRowIndexByKey.get(key);
                if (existing == null) {
                    serverRowIndexByKey.put(key, uniqueServerRows.size());
                    uniqueServerRows.add(serverRow);
                    pendingToUniqueRow[i] = uniqueServerRows.size() - 1;
                } else {
                    uniqueServerRows.set(existing, serverRow);
                    pendingToUniqueRow[i] = existing;
                }
            }

            // Parent rows first: the tracked_servers upsert returns the canonical uuid of every
            // row (a losing random candidate from a concurrent flush on the same new server is
            // discarded by ON CONFLICT (ip, port)); the child rows below must reference a uuid
            // that actually exists in tracked_servers or the foreign keys reject them.
            List<UUID> serverUuids = executeChunkedReturningUuids(serverUpsertSql(), rows -> placeholders(rows, 24), uniqueServerRows);

            List<Object[]> playerMatches = new ArrayList<>();
            List<Object[]> playerInserts = new ArrayList<>();
            for (int i = 0; i < resolved.size(); i++) {
                Resolved r = resolved.get(i);
                Pending pending = r.pending();
                UUID serverUuid = serverUuids.get(pendingToUniqueRow[i]);
                ServerTrackerVerifier.ServerSnapshot snapshot = pending.snapshot();
                for (HoneypotDetector.SampleEntry entry : snapshot.players()) {
                    String username = StringUtils.truncate(entry.name(), MAX_USERNAME_LENGTH);
                    playerMatches.add(new Object[]{serverUuid, entry.uuid(), username, pending.seenAt()});
                    playerInserts.add(new Object[]{serverUuid, entry.uuid(), username, pending.seenAt(), pending.seenAt()});
                }
            }

            // Existing player rows first (times_seen++), then insert missing rows (times_seen = 1);
            // the insert count is the number of first sightings.
            if (!playerMatches.isEmpty()) {
                executeChunked(playerUpdateSql(), rows -> placeholders(rows, 4), playerMatches);
            }
            int firstSightings = 0;
            if (!playerInserts.isEmpty()) {
                firstSightings = executeChunkedReturningCount(playerInsertSql(), ServerTrackerStore::playerInsertValues, playerInserts);
                if (metrics != null && firstSightings > 0) {
                    metrics.recordPlayersSeenFirst(firstSightings);
                }
            }
            return batch.size();
        } catch (Exception e) {
            log.warn("Failed to flush tracker store batch of {} snapshot(s): {}", batch.size(), e.toString());
            return 0;
        }
    }

    private Geo lookupGeo(String ip) {
        if (!geoEnabled) {
            return new Geo(null, null, false);
        }
        try {
            IpLookup lookup = maxMindService.lookupIp(ip);
            GeoLocation location = lookup.location();
            AsnLookup asn = lookup.asn();
            String country = location != null ? location.countryCode() : null;
            Long asnNumber = null;
            if (asn != null && asn.asn() != null && asn.asn().startsWith("AS")) {
                try {
                    asnNumber = Long.parseLong(asn.asn().substring(2));
                } catch (NumberFormatException ignored) {
                    // non-numeric ASN string; leave null
                }
            }
            return new Geo(country, asnNumber, false);
        } catch (NotFoundException e) {
            return new Geo(null, null, true);
        } catch (Exception e) {
            log.debug("Geo lookup failed for {}: {}", ip, e.toString());
            return new Geo(null, null, true);
        }
    }

    private void executeChunked(String sql, Function<Integer, String> valuesBuilder, List<Object[]> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i += CHUNK_SIZE) {
            List<Object[]> slice = rows.subList(i, Math.min(i + CHUNK_SIZE, rows.size()));
            jdbcTemplate.update(sql.formatted(valuesBuilder.apply(slice.size())), setterFor(flatten(slice)));
        }
    }

    private int executeChunkedReturningCount(String sql, Function<Integer, String> valuesBuilder, List<Object[]> rows) {
        int total = 0;
        for (int i = 0; i < rows.size(); i += CHUNK_SIZE) {
            List<Object[]> slice = rows.subList(i, Math.min(i + CHUNK_SIZE, rows.size()));
            total += jdbcTemplate.update(sql.formatted(valuesBuilder.apply(slice.size())), setterFor(flatten(slice)));
        }
        return total;
    }

    /**
     * Upserts the tracker_servers rows and returns the canonical uuid of each, in input order.
     * {@code ON CONFLICT DO UPDATE ... RETURNING} emits one row per VALUES row (the inserted row,
     * or the row as updated after any conflict) — including two rows targeting the same
     * (ip, port), which both yield the uuid that survived the upsert.
     */
    private List<UUID> executeChunkedReturningUuids(String sql, Function<Integer, String> valuesBuilder, List<Object[]> rows) {
        List<UUID> uuids = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i += CHUNK_SIZE) {
            List<Object[]> slice = rows.subList(i, Math.min(i + CHUNK_SIZE, rows.size()));
            uuids.addAll(jdbcTemplate.query(
                    sql.formatted(valuesBuilder.apply(slice.size())),
                    setterFor(flatten(slice)),
                    (rs, rowNum) -> UUID.fromString(rs.getString("uuid"))
            ));
        }
        if (uuids.size() != rows.size()) {
            throw new IllegalStateException("tracker_servers upsert returned " + uuids.size() + " uuids for " + rows.size() + " rows");
        }
        return uuids;
    }

    private static PreparedStatementSetter setterFor(Object[] flat) {
        return ps -> {
            for (int i = 0; i < flat.length; i++) {
                Object value = flat[i] instanceof Instant instant ? java.sql.Timestamp.from(instant) : flat[i];
                ps.setObject(i + 1, value);
            }
        };
    }

    private static String placeholders(int rows, int columns) {
        StringBuilder sb = new StringBuilder(rows * columns * 3);
        for (int r = 0; r < rows; r++) {
            if (r > 0) {
                sb.append(',');
            }
            sb.append('(');
            for (int c = 0; c < columns; c++) {
                if (c > 0) {
                    sb.append(',');
                }
                sb.append('?');
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static Object[] flatten(List<Object[]> rows) {
        int columns = rows.get(0).length;
        Object[] flat = new Object[rows.size() * columns];
        int i = 0;
        for (Object[] row : rows) {
            System.arraycopy(row, 0, flat, i, columns);
            i += columns;
        }
        return flat;
    }

    private static String playerUpdateSql() {
        return """
                UPDATE tracker_player_history SET username = v.username, last_seen = v.last_seen::timestamptz, times_seen = tracker_player_history.times_seen + 1
                FROM (VALUES %s) AS v(server_uuid, player_uuid, username, last_seen)
                WHERE tracker_player_history.server_uuid = v.server_uuid::uuid AND tracker_player_history.player_uuid = v.player_uuid::uuid
                """;
    }

    private static String playerInsertSql() {
        return """
                INSERT INTO tracker_player_history (server_uuid, player_uuid, username, first_seen, last_seen, times_seen)
                VALUES %s
                ON CONFLICT (server_uuid, player_uuid) DO NOTHING
                """;
    }

    private static String playerInsertValues(int rows) {
        StringBuilder sb = new StringBuilder(rows * 18);
        for (int r = 0; r < rows; r++) {
            if (r > 0) {
                sb.append(',');
            }
            sb.append("(?,?,?,?,?,1)");
        }
        return sb.toString();
    }

    private static String serverUpsertSql() {
        return """
                INSERT INTO tracker_servers (uuid, ip, port, first_seen, last_updated, online_count, max_players,
                    version, protocol, platform, motd, motd_hash, favicon_hash, modded, latency_ms,
                    prevents_chat_reports, enforces_secure_chat, previews_chat, country, asn,
                    sample_count, honeypot, consecutive_offline, last_refreshed)
                VALUES %s
                ON CONFLICT (ip, port) DO UPDATE SET
                    last_updated = EXCLUDED.last_updated,
                    online_count = EXCLUDED.online_count,
                    max_players = EXCLUDED.max_players,
                    version = EXCLUDED.version,
                    protocol = EXCLUDED.protocol,
                    platform = EXCLUDED.platform,
                    motd = EXCLUDED.motd,
                    motd_hash = EXCLUDED.motd_hash,
                    favicon_hash = EXCLUDED.favicon_hash,
                    modded = EXCLUDED.modded,
                    latency_ms = EXCLUDED.latency_ms,
                    prevents_chat_reports = EXCLUDED.prevents_chat_reports,
                    enforces_secure_chat = EXCLUDED.enforces_secure_chat,
                    previews_chat = EXCLUDED.previews_chat,
                    country = COALESCE(EXCLUDED.country, tracker_servers.country),
                    asn = COALESCE(EXCLUDED.asn, tracker_servers.asn),
                    sample_count = EXCLUDED.sample_count,
                    honeypot = tracker_servers.honeypot OR EXCLUDED.honeypot,
                    consecutive_offline = 0
                RETURNING uuid
                """;
    }

    private record Geo(String country, Long asn, boolean failed) {}
}