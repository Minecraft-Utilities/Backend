package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.mcutils.backend.model.persistence.postgres.PlayerSkinAdoptionId;
import xyz.mcutils.backend.model.persistence.postgres.PlayerSkinAdoptionRow;

import java.util.List;
import java.util.UUID;

public interface PlayerSkinAdoptionRepository extends JpaRepository<PlayerSkinAdoptionRow, PlayerSkinAdoptionId> {
    /**
     * JOIN FETCH mirrors {@link PlayerCapeAdoptionRepository}; without it, every
     * {@code adoption.getSkin()} call issued a separate lazy SELECT (N+1 on player
     * profile/search responses).
     */
    @Query("""
            SELECT a FROM PlayerSkinAdoptionRow a
            JOIN FETCH a.skin
            WHERE a.playerId = :playerId
            ORDER BY a.firstSeen ASC
            """)
    List<PlayerSkinAdoptionRow> findByPlayerIdOrderByFirstSeenAsc(@Param("playerId") UUID playerId);
}
