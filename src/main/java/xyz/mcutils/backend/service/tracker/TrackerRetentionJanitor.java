package xyz.mcutils.backend.service.tracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.metric.impl.tracker.ServerTrackerMetric;
import xyz.mcutils.backend.service.MetricService;

/**
 * Retention janitor for {@code tracker_server_online_history} (grows ~400k rows/day at the default 6h
 * refresh gap). Runs daily: raw samples older than {@code raw-retention-days} are downsampled
 * into hourly buckets (peak online per bucket), the raw rows are then deleted, and everything
 * older than {@code hourly-retention-days} is pruned. {@code tracker_player_history} is bounded by
 * distinct (server, player) pairs and needs no janitor.
 */
@Service
@Slf4j
public class TrackerRetentionJanitor {

    private static final String DOWNSAMPLE_SQL = """
            INSERT INTO tracker_server_online_history (server_uuid, sampled_at, online, max, version)
            SELECT server_uuid, hour, online, max, version
            FROM (
                SELECT server_uuid, date_trunc('hour', sampled_at) AS hour, online, max, version,
                       ROW_NUMBER() OVER (PARTITION BY server_uuid, date_trunc('hour', sampled_at) ORDER BY online DESC) AS rn
                FROM tracker_server_online_history
                WHERE sampled_at < now() - make_interval(days => ?)
            ) t
            WHERE rn = 1
            ON CONFLICT (server_uuid, sampled_at) DO NOTHING
            """;

    private static final String DELETE_RAW_SQL = """
            DELETE FROM tracker_server_online_history
            WHERE sampled_at < now() - make_interval(days => ?)
              AND date_trunc('hour', sampled_at) <> sampled_at
            """;

    private static final String DELETE_OLD_SQL = """
            DELETE FROM tracker_server_online_history
            WHERE sampled_at < now() - make_interval(days => ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final boolean trackingEnabled;
    private final int rawRetentionDays;
    private final int hourlyRetentionDays;

    public TrackerRetentionJanitor(
            JdbcTemplate jdbcTemplate,
            @Value("${mc-utils.server-tracker.tracking.enabled:true}") boolean trackingEnabled,
            @Value("${mc-utils.server-tracker.history.raw-retention-days:30}") int rawRetentionDays,
            @Value("${mc-utils.server-tracker.history.hourly-retention-days:90}") int hourlyRetentionDays
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.trackingEnabled = trackingEnabled;
        this.rawRetentionDays = Math.max(1, rawRetentionDays);
        this.hourlyRetentionDays = Math.max(this.rawRetentionDays, hourlyRetentionDays);
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void prune() {
        if (!trackingEnabled) {
            return;
        }
        try {
            jdbcTemplate.update(DOWNSAMPLE_SQL, rawRetentionDays);
            int rawPruned = jdbcTemplate.update(DELETE_RAW_SQL, rawRetentionDays);
            int oldPruned = jdbcTemplate.update(DELETE_OLD_SQL, hourlyRetentionDays);
            long pruned = (long) rawPruned + oldPruned;
            ServerTrackerMetric metrics = MetricService.getMetric(ServerTrackerMetric.class);
            if (metrics != null && pruned > 0) {
                metrics.recordHistoryPruned(pruned);
            }
            log.info("Tracker online-history retention: downsampled raw rows (<{}d) to hourly, pruned {} raw + {} old rows",
                    rawRetentionDays, rawPruned, oldPruned);
        } catch (Exception e) {
            log.warn("Tracker online-history retention failed: {}", e.toString());
        }
    }
}