package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.PlayerCapeAdoptionId;
import xyz.mcutils.backend.model.persistence.postgres.PlayerCapeAdoptionRow;

import java.util.List;
import java.util.UUID;

public interface PlayerCapeAdoptionRepository extends JpaRepository<PlayerCapeAdoptionRow, PlayerCapeAdoptionId> {
    List<PlayerCapeAdoptionRow> findByPlayerIdOrderByFirstSeenAsc(UUID playerId);
}
