package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;

import java.time.Instant;
import java.util.Optional;

public interface CapeRepository extends JpaRepository<CapeRow, Long> {
    Optional<CapeRow> findByTextureId(String textureId);

    @Query("SELECT c FROM CapeRow c ORDER BY c.uniqueOwners DESC, c.id ASC")
    Slice<CapeRow> findAllOrderByUniqueOwnersDescIdAsc(Pageable pageable);

    @Query(nativeQuery = true, value = """
        INSERT INTO capes (name, texture_id, unique_owners, first_seen)
        VALUES (:name, :textureId, 0, :firstSeen)
        ON CONFLICT (texture_id) DO NOTHING
        RETURNING *
        """)
    Optional<CapeRow> insertIfAbsent(@Param("name") String name,
                                     @Param("textureId") String textureId,
                                     @Param("firstSeen") Instant firstSeen);
}