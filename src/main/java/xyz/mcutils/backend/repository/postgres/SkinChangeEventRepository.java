package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
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

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        UPDATE skins s
        SET unique_owners = s.unique_owners + delta.cnt
        FROM (
            SELECT sce.skin_id, COUNT(*) AS cnt
            FROM skin_change_events sce
            WHERE sce.id IN (:ids)
              AND NOT EXISTS (
                  SELECT 1 FROM skin_change_events prev
                  WHERE prev.skin_id = sce.skin_id
                    AND prev.player_id = sce.player_id
                    AND prev.id <> sce.id
              )
            GROUP BY sce.skin_id
        ) delta
        WHERE s.id = delta.skin_id
        """)
    void bulkUpdateUniqueOwners(@Param("ids") List<Long> ids);
}
