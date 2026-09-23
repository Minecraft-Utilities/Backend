package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.mcutils.backend.model.persistence.postgres.PlayerHistoryRow;
import xyz.mcutils.backend.model.persistence.postgres.TrackedServerRow;

import java.time.Instant;
import java.util.UUID;

public interface PlayerHistoryRepository extends JpaRepository<PlayerHistoryRow, PlayerHistoryRow.PlayerHistoryId> {
    @Query("""
        SELECT COUNT(DISTINCT ph.playerUuid)
        FROM PlayerHistoryRow ph
        JOIN TrackedServerRow s ON s.uuid = ph.serverUuid
        WHERE s.honeypot = false
        """)
    long countDistinctPlayers();

    @Query("""
        SELECT COUNT(ph)
        FROM PlayerHistoryRow ph
        JOIN TrackedServerRow s ON s.uuid = ph.serverUuid
        WHERE ph.playerUuid = :playerUuid
          AND s.honeypot = false
        """)
    long countPublicSightingsByPlayerUuid(@Param("playerUuid") UUID playerUuid);

    @Query("""
        SELECT ph.username AS username,
               ph.firstSeen AS firstSeen,
               ph.lastSeen AS lastSeen,
               ph.timesSeen AS timesSeen,
               s.uuid AS serverUuid,
               s.ip AS ip,
               s.port AS port,
               s.version AS version,
               s.protocol AS protocol,
               s.platform AS platform,
               s.onlineCount AS onlineCount,
               s.maxPlayers AS maxPlayers,
               s.country AS country,
               (s.consecutiveOffline = 0) AS online,
               s.lastUpdated AS lastUpdated
        FROM PlayerHistoryRow ph
        JOIN TrackedServerRow s ON s.uuid = ph.serverUuid
        WHERE ph.playerUuid = :playerUuid
          AND s.honeypot = false
        ORDER BY ph.lastSeen DESC, s.uuid ASC
        """)
    Page<PlayerHistoryRepository.PlayerSightingProjection> findPublicSightingsByPlayerUuid(
            @Param("playerUuid") UUID playerUuid,
            Pageable pageable
    );

    interface PlayerSightingProjection {
        String getUsername();
        Instant getFirstSeen();
        Instant getLastSeen();
        int getTimesSeen();
        UUID getServerUuid();
        String getIp();
        int getPort();
        String getVersion();
        Integer getProtocol();
        String getPlatform();
        int getOnlineCount();
        int getMaxPlayers();
        String getCountry();
        boolean getOnline();
        Instant getLastUpdated();
    }
}