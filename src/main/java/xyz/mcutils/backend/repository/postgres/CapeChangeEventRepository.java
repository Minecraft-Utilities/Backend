package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.mcutils.backend.model.persistence.postgres.CapeChangeEventRow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapeChangeEventRepository extends JpaRepository<CapeChangeEventRow, Long> {
    List<CapeChangeEventRow> findByPlayerId(UUID playerId);

    @Query("SELECT e FROM CapeChangeEventRow e WHERE e.cape.id = :capeId ORDER BY e.timestamp ASC LIMIT 1")
    Optional<CapeChangeEventRow> findFirstByCapeId(long capeId);

    @Query("SELECT e.cape.id, MIN(e.timestamp) FROM CapeChangeEventRow e WHERE e.cape.id IN :capeIds GROUP BY e.cape.id")
    List<Object[]> findFirstTimestampsByCapeIds(Collection<Long> capeIds);
}
