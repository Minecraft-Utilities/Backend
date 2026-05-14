package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
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

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        UPDATE capes c
        SET unique_owners = c.unique_owners + delta.cnt
        FROM (
            SELECT cce.cape_id, COUNT(*) AS cnt
            FROM cape_change_events cce
            WHERE cce.id IN (:ids)
              AND NOT EXISTS (
                  SELECT 1 FROM cape_change_events prev
                  WHERE prev.cape_id = cce.cape_id
                    AND prev.player_id = cce.player_id
                    AND prev.id <> cce.id
              )
            GROUP BY cce.cape_id
        ) delta
        WHERE c.id = delta.cape_id
        """)
    void bulkUpdateUniqueOwners(@Param("ids") List<Long> ids);
}
