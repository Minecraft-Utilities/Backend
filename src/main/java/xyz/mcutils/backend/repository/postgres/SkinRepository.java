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

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "UPDATE skins SET trending_heat = 0 WHERE trending_heat > 0")
    void resetTrendingHeat();

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        UPDATE skins
        SET trending_heat = subquery.trending_heat
        FROM (
            SELECT psa.skin_id, COUNT(DISTINCT psa.player_id) AS trending_heat
            FROM player_skin_adoptions psa
            WHERE psa.last_equipped_at IS NOT NULL
              AND psa.last_equipped_at >= NOW() - INTERVAL '7 days'
            GROUP BY psa.skin_id
        ) AS subquery
        WHERE skins.id = subquery.skin_id
        """)
    void updateTrendingHeat();
}
