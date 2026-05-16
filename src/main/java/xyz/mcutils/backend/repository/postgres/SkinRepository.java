package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;

import java.time.Instant;
import java.util.Optional;

public interface SkinRepository extends JpaRepository<SkinRow, Long> {
    Optional<SkinRow> findByTextureId(String textureId);

    @Query("SELECT s FROM SkinRow s")
    Slice<SkinRow> findAllSkins(Pageable pageable);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO skins (texture_id, model, legacy, unique_owners, first_seen)
        VALUES (:textureId, CAST(:model AS skin_model), :legacy, 0, :firstSeen)
        ON CONFLICT (texture_id) DO NOTHING
        """)
    int insertIfAbsent(@Param("textureId") String textureId,
                       @Param("model") String model,
                       @Param("legacy") boolean legacy,
                       @Param("firstSeen") Instant firstSeen
    );

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        UPDATE skins
        SET trending_heat = COALESCE(subquery.trending_heat, 0)
        FROM (
            SELECT s.id AS skin_id, COUNT(DISTINCT sce.player_id) AS trending_heat
            FROM skins s
            LEFT JOIN skin_change_events sce ON s.id = sce.skin_id
                AND sce.timestamp >= NOW() - INTERVAL '7 days'
                AND EXISTS (
                    SELECT 1
                    FROM skin_change_events prev
                    WHERE prev.player_id = sce.player_id
                      AND prev.timestamp < sce.timestamp
                )
            GROUP BY s.id
        ) AS subquery
        WHERE skins.id = subquery.skin_id
        """)
    void updateTrendingHeat();
}