package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerCapeAdoptionId;
import xyz.mcutils.backend.model.persistence.postgres.PlayerCapeAdoptionRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PlayerCapeAdoptionRepository extends JpaRepository<PlayerCapeAdoptionRow, PlayerCapeAdoptionId> {
    List<PlayerCapeAdoptionRow> findByPlayerIdOrderByFirstSeenAsc(UUID playerId);

    /**
     * Records that a player was first tracked with this cape.
     * Does not set {@code last_equipped_at}, so it does not affect trending heat.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO player_cape_adoptions (player_id, cape_id, first_seen, last_equipped_at)
        VALUES (:playerId, :capeId, :timestamp, NULL)
        ON CONFLICT (player_id, cape_id) DO NOTHING
        """)
    void insertFirstAdoption(@Param("playerId") UUID playerId,
                             @Param("capeId") long capeId,
                             @Param("timestamp") Instant timestamp);

    /**
     * Records that a player equipped this cape via a change (or re-equip after switching away).
     * Updates {@code last_equipped_at}, which would drive cape trending if added later.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO player_cape_adoptions (player_id, cape_id, first_seen, last_equipped_at)
        VALUES (:playerId, :capeId, :timestamp, :timestamp)
        ON CONFLICT (player_id, cape_id) DO UPDATE
            SET last_equipped_at = EXCLUDED.last_equipped_at
        """)
    void recordEquip(@Param("playerId") UUID playerId,
                     @Param("capeId") long capeId,
                     @Param("timestamp") Instant timestamp);
}
