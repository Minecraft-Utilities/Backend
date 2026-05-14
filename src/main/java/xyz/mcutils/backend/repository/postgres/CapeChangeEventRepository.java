package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.CapeChangeEventRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinChangeEventRow;

import java.util.List;
import java.util.UUID;

public interface CapeChangeEventRepository extends JpaRepository<CapeChangeEventRow, Long> {
    List<CapeChangeEventRow> findByPlayerId(UUID playerId);
}
