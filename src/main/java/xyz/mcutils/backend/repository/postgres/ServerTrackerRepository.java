package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerTrackerRepository extends JpaRepository<TrackedServerRow, UUID> {
    Optional<TrackedServerRow> findByIpAndPort(String ip, int port);

    @Query("SELECT COUNT(t) FROM TrackedServerRow t")
    long countTrackedServers();

    /**
     * Distinct players currently present in the most recent player sample of an alive,
     * non-honeypot server. The server-advertised {@code online_count} is untrusted (servers can
     * falsify it), so only players actually observed in a sample count. {@code last_seen} is
     * the absorption time of the snapshot that contained the player; the grace seconds absorb
     * the store flush window so a player seen in the latest absorbed sample still matches a
     * marginally newer {@code last_updated}. Players drop out at the first refresh whose sample
     * no longer contains them (refresh cadence is hours, far beyond the grace).
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT ph.player_uuid
                FROM tracker_player_history ph
                JOIN tracker_servers s ON s.uuid = ph.server_uuid
                WHERE s.consecutive_offline = 0
                  AND s.honeypot = false
                  AND ph.last_seen >= s.last_updated - make_interval(secs => ?1)
            ) verified
            """, nativeQuery = true)
    long countVerifiedOnlinePlayers(@Param("graceSeconds") int graceSeconds);

    @Query("SELECT t.country AS groupKey, COUNT(t) AS total FROM TrackedServerRow t WHERE t.country IS NOT NULL GROUP BY t.country ORDER BY COUNT(t) DESC")
    List<Breakdown> topCountries(Pageable pageable);

    @Query("SELECT LOWER(COALESCE(t.platform, 'unknown')) AS groupKey, COUNT(t) AS total FROM TrackedServerRow t GROUP BY LOWER(COALESCE(t.platform, 'unknown')) ORDER BY COUNT(t) DESC")
    List<Breakdown> topPlatforms(Pageable pageable);

    @Query("SELECT t.protocol AS groupKey, COUNT(t) AS total FROM TrackedServerRow t WHERE t.protocol IS NOT NULL AND t.protocol <> -1 GROUP BY t.protocol ORDER BY COUNT(t) DESC")
    List<Breakdown> topProtocols(Pageable pageable);

    interface Breakdown {
        String getGroupKey();

        long getTotal();
    }
}