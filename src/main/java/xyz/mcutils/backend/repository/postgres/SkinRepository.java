package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;

import java.util.Optional;

public interface SkinRepository extends JpaRepository<SkinRow, Long> {
    Optional<SkinRow> findByTextureId(String textureId);

    @Query("SELECT s FROM SkinRow s ORDER BY s.uniqueOwners DESC, s.id ASC")
    Page<SkinRow> findAllOrderByUniqueOwnersDescIdAsc(Pageable pageable);
}