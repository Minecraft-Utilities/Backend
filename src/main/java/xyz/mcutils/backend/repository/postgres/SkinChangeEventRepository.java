package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.mcutils.backend.model.persistence.postgres.SkinChangeEventRow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkinChangeEventRepository extends JpaRepository<SkinChangeEventRow, Long> {
    List<SkinChangeEventRow> findByPlayerId(UUID playerId);

    @Query("SELECT e FROM SkinChangeEventRow e WHERE e.skin.id = :skinId ORDER BY e.timestamp ASC LIMIT 1")
    Optional<SkinChangeEventRow> findFirstBySkinId(long skinId);

    @Query("SELECT e.skin.id, MIN(e.timestamp) FROM SkinChangeEventRow e WHERE e.skin.id IN :skinIds GROUP BY e.skin.id")
    List<Object[]> findFirstTimestampsBySkinIds(Collection<Long> skinIds);

}
