package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerTrackerRepository extends JpaRepository<TrackedServerRow, UUID> {
    Optional<TrackedServerRow> findByIpAndPort(String ip, int port);

    @Query("SELECT COUNT(t) FROM TrackedServerRow t")
    long countTrackedServers();

    @Query("SELECT COALESCE(SUM(t.onlineCount), 0) FROM TrackedServerRow t WHERE t.consecutiveOffline = 0 AND t.honeypot = false")
    long sumOnlinePlayers();

    @Query("SELECT t.country AS groupKey, COUNT(t) AS total FROM TrackedServerRow t WHERE t.country IS NOT NULL GROUP BY t.country ORDER BY COUNT(t) DESC")
    List<Breakdown> topCountries(Pageable pageable);

    @Query("SELECT LOWER(COALESCE(t.platform, 'unknown')) AS groupKey, COUNT(t) AS total FROM TrackedServerRow t GROUP BY LOWER(COALESCE(t.platform, 'unknown')) ORDER BY COUNT(t) DESC")
    List<Breakdown> topPlatforms(Pageable pageable);

    @Query("SELECT t.protocol AS groupKey, COUNT(t) AS total FROM TrackedServerRow t WHERE t.protocol IS NOT NULL GROUP BY t.protocol ORDER BY COUNT(t) DESC")
    List<Breakdown> topProtocols(Pageable pageable);

    interface Breakdown {
        String getGroupKey();

        long getTotal();
    }
}