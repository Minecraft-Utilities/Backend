package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.mcutils.backend.model.persistence.postgres.CapeRow;

import java.util.Optional;

public interface CapeRepository extends JpaRepository<CapeRow, Long> {
    Optional<CapeRow> findByTextureId(String textureId);

    @Query("SELECT c FROM CapeRow c ORDER BY c.uniqueOwners DESC, c.id ASC")
    Page<CapeRow> findAllOrderByUniqueOwnersDescIdAsc(Pageable pageable);
}