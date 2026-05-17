package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;

import java.time.Instant;
import java.util.*;

public interface PlayerRepository extends JpaRepository<PlayerRow, UUID> {
    Optional<PlayerRow> findByUsernameIgnoreCase(String username);
    List<PlayerRow> findByUsernameStartingWithIgnoreCase(String username, Pageable pageable);
    List<PlayerRow> findTopByOrderBySubmittedUuidsDesc(Pageable pageable);

    @Query("SELECT p.username FROM PlayerRow p WHERE p.skin.id = :skinId")
    List<String> findUsernamesBySkinId(long skinId, Pageable pageable);

    @Query("SELECT p.username FROM PlayerRow p WHERE p.cape.id = :capeId")
    List<String> findUsernamesByCapeId(long capeId, Pageable pageable);

    @Query("SELECT p.id FROM PlayerRow p WHERE p.id IN :ids")
    Set<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    @Query("SELECT p.skin FROM PlayerRow p WHERE p.id = :id")
    Optional<SkinRow> findSkinById(@Param("id") UUID id);

    @Query("SELECT p.skin FROM PlayerRow p WHERE UPPER(p.username) = UPPER(:username)")
    Optional<SkinRow> findSkinByUsernameIgnoreCase(@Param("username") String username);

    @Modifying
    @Transactional
    @Query("UPDATE PlayerRow p SET p.submittedUuids = p.submittedUuids + :count WHERE p.id = :id")
    void incrementSubmittedUuids(@Param("id") UUID id, @Param("count") long count);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        UPDATE players
        SET monthly_views = 0
        WHERE monthly_views > 0
        AND id NOT IN (
            SELECT DISTINCT player_id FROM player_view_events
            WHERE viewed_at >= NOW() - INTERVAL '30 days'
        );
    """)
    void resetMonthlyViews();

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        UPDATE players
         SET monthly_views = subquery.monthly_views
         FROM (
             SELECT player_id, COUNT(*) AS monthly_views
             FROM player_view_events
             WHERE viewed_at >= NOW() - INTERVAL '30 days'
             GROUP BY player_id
         ) AS subquery
         WHERE players.id = subquery.player_id
         AND players.monthly_views != subquery.monthly_views;
    """)
    void updateMonthlyViews();

    @Query(value = """
    SELECT * FROM players
    WHERE last_updated < :cutoff
    ORDER BY priority_score DESC
    LIMIT :limit
""", nativeQuery = true)
    List<PlayerRow> findPlayersForRefresh(
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit
    );
}