package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerSkinAdoptionId;
import xyz.mcutils.backend.model.persistence.postgres.PlayerSkinAdoptionRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PlayerSkinAdoptionRepository extends JpaRepository<PlayerSkinAdoptionRow, PlayerSkinAdoptionId> {
    List<PlayerSkinAdoptionRow> findByPlayerIdOrderByFirstSeenAsc(UUID playerId);

    /**
     * Insert or update an adoption row.
     * On first equip: sets both first_seen and last_equipped_at.
     * On re-equip: updates only last_equipped_at; first_seen is preserved.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO player_skin_adoptions (player_id, skin_id, first_seen, last_equipped_at)
        VALUES (:playerId, :skinId, :timestamp, :timestamp)
        ON CONFLICT (player_id, skin_id) DO UPDATE
            SET last_equipped_at = EXCLUDED.last_equipped_at
        """)
    void upsert(@Param("playerId") UUID playerId,
                @Param("skinId") long skinId,
                @Param("timestamp") Instant timestamp);
}
