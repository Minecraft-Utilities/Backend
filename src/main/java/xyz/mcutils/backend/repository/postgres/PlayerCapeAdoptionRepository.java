package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.mcutils.backend.model.persistence.postgres.PlayerCapeAdoptionId;
import xyz.mcutils.backend.model.persistence.postgres.PlayerCapeAdoptionRow;

import java.util.List;
import java.util.UUID;

public interface PlayerCapeAdoptionRepository extends JpaRepository<PlayerCapeAdoptionRow, PlayerCapeAdoptionId> {
    @Query("""
            SELECT a FROM PlayerCapeAdoptionRow a
            JOIN FETCH a.cape
            WHERE a.playerId = :playerId
            ORDER BY a.firstSeen ASC
            """)
    List<PlayerCapeAdoptionRow> findByPlayerIdOrderByFirstSeenAsc(@Param("playerId") UUID playerId);
}
