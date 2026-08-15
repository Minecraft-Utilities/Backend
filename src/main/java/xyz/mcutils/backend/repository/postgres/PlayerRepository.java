package xyz.mcutils.backend.repository.postgres;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;

import java.time.Instant;
import java.util.*;

public interface PlayerRepository extends JpaRepository<PlayerRow, UUID> {
    Optional<PlayerRow> findByUsernameIgnoreCase(String username);
    List<PlayerRow> findByUsernameStartingWithIgnoreCase(String username, Pageable pageable);
    List<PlayerRow> findAllByOrderBySubmittedUuidsDesc(Pageable pageable);

    /**
     * Claims the most volatile due players first: those with {@code change_velocity} at or above
     * {@link xyz.mcutils.backend.service.PlayerRefreshSchedule#HOT_VELOCITY_THRESHOLD} or with any
     * monthly views, ordered by change velocity descending so the most-changed players are always
     * refreshed at their adaptive cadence even when the loop is overloaded (where a plain
     * {@code next_refresh_at} FIFO order would flatten every player to the same effective rate).
     * <p>
     * The {@code 0.5} literal MUST stay in sync with {@code HOT_VELOCITY_THRESHOLD} and the partial
     * index predicate in {@code V39__hot_refresh_priority_index.sql}; a parameter would defeat
     * partial index matching.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT p FROM PlayerRow p
            WHERE p.nextRefreshAt < :now
              AND (p.changeVelocity >= 0.5 OR p.monthlyViews > 0)
            ORDER BY p.changeVelocity DESC, p.monthlyViews DESC, p.nextRefreshAt ASC, p.id ASC
            """)
    List<PlayerRow> findHotDueForRefreshSkippable(@Param("now") Instant now, Pageable pageable);

    /**
     * Claims the remaining stable players (below {@link xyz.mcutils.backend.service.PlayerRefreshSchedule#HOT_VELOCITY_THRESHOLD}
     * and never viewed) in FIFO order by {@code next_refresh_at}; fills the chunk capacity left
     * over by the hot tier. Complement of {@link #findHotDueForRefreshSkippable}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT p FROM PlayerRow p
            WHERE p.nextRefreshAt < :now
              AND p.changeVelocity < 0.5 AND p.monthlyViews = 0
            ORDER BY p.nextRefreshAt ASC, p.id ASC
            """)
    List<PlayerRow> findColdDueForRefreshSkippable(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE PlayerRow p SET p.nextRefreshAt = :leaseUntil WHERE p.id IN :ids")
    void leasePlayersForRefresh(@Param("ids") Collection<UUID> ids, @Param("leaseUntil") Instant leaseUntil);

    @Query("SELECT p.username FROM PlayerRow p WHERE p.skin.id = :skinId")
    List<String> findUsernamesBySkinId(long skinId, Pageable pageable);

    @Query("SELECT p.username FROM PlayerRow p WHERE p.cape.id = :capeId")
    List<String> findUsernamesByCapeId(long capeId, Pageable pageable);

    @Query("SELECT p.id FROM PlayerRow p WHERE p.id IN :ids")
    Set<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    @Query("SELECT p FROM PlayerRow p WHERE p.id > :cursor ORDER BY p.id ASC")
    List<PlayerRow> findPlayersAfter(@Param("cursor") UUID cursor, Pageable pageable);

    @Query("SELECT UPPER(p.username) FROM PlayerRow p WHERE UPPER(p.username) IN :usernames")
    Set<String> findExistingUsernamesUpper(@Param("usernames") Collection<String> usernames);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlayerRow p WHERE p.id = :id")
    Optional<PlayerRow> findByIdForUpdate(@Param("id") UUID id);

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
    @Query("UPDATE PlayerRow p SET p.nextRefreshAt = :nextRefreshAt WHERE p.id IN :ids")
    void bumpRefreshFailure(@Param("ids") Collection<UUID> ids, @Param("nextRefreshAt") Instant nextRefreshAt);

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
}
