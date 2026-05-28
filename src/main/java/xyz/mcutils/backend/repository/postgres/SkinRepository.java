package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;

import java.util.Optional;

public interface SkinRepository extends JpaRepository<SkinRow, Long> {
    Optional<SkinRow> findByTextureId(String textureId);

    @Query("SELECT s FROM SkinRow s")
    Slice<SkinRow> findAllSkins(Pageable pageable);

    @Query("SELECT s FROM SkinRow s WHERE s.trendingHeat > 0")
    Slice<SkinRow> findTrendingSkins(Pageable pageable);

    long countByTrendingHeatGreaterThan(int trendingHeat);

    @Modifying
    @Transactional
    @Query("UPDATE SkinRow s SET s.legacy = :legacy WHERE s.textureId = :textureId")
    int updateLegacyByTextureId(@Param("textureId") String textureId, @Param("legacy") boolean legacy);

    /**
     * Rebuilds {@code trending_heat} from adoptions in the last 7 days.
     * Single scan of {@code player_skin_adoptions}; skips unchanged scores; clears stale rows.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        WITH agg AS MATERIALIZED (
            SELECT psa.skin_id, COUNT(*)::int AS trending_heat
            FROM player_skin_adoptions psa
            WHERE psa.last_equipped_at >= NOW() - INTERVAL '7 days'
            GROUP BY psa.skin_id
        ),
        apply_scores AS (
            UPDATE skins s
            SET trending_heat = a.trending_heat
            FROM agg a
            WHERE s.id = a.skin_id
              AND s.trending_heat IS DISTINCT FROM a.trending_heat
            RETURNING s.id
        )
        UPDATE skins s
        SET trending_heat = 0
        WHERE s.trending_heat > 0
          AND NOT EXISTS (SELECT 1 FROM agg a WHERE a.skin_id = s.id)
        """)
    void rebuildTrendingHeat();
}
