package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;

import java.time.Instant;
import java.util.Optional;

public interface CapeRepository extends JpaRepository<CapeRow, Long> {
    Optional<CapeRow> findByTextureId(String textureId);

    @Query("SELECT c FROM CapeRow c ORDER BY c.uniqueOwners DESC, c.id ASC")
    Page<CapeRow> findAllOrderByUniqueOwnersDescIdAsc(Pageable pageable);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
        INSERT INTO capes (name, texture_id, unique_owners, first_seen)
        VALUES (:name, :textureId, 0, :firstSeen)
        ON CONFLICT (texture_id) DO NOTHING
        """)
    int insertIfAbsent(@Param("name") String name,
                       @Param("textureId") String textureId,
                       @Param("firstSeen") Instant firstSeen);
}