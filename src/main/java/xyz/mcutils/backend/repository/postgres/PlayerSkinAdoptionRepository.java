package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.PlayerSkinAdoptionId;
import xyz.mcutils.backend.model.persistence.postgres.PlayerSkinAdoptionRow;

import java.util.List;
import java.util.UUID;

public interface PlayerSkinAdoptionRepository extends JpaRepository<PlayerSkinAdoptionRow, PlayerSkinAdoptionId> {
    List<PlayerSkinAdoptionRow> findByPlayerIdOrderByFirstSeenAsc(UUID playerId);
}
