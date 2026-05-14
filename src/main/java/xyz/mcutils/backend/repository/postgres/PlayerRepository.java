package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<PlayerRow, UUID> {
    Optional<PlayerRow> findByUsernameIgnoreCase(String username);
    List<PlayerRow> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
    List<PlayerRow> findTopByOrderBySubmittedUuidsDesc(Pageable pageable);
    Page<PlayerRow> findAllByLastUpdatedBeforeOrderByLastUpdatedAsc(Instant cutoff, Pageable pageable);

    @Query("SELECT p FROM PlayerRow p WHERE p.skin.id = :skinId")
    List<PlayerRow> findBySkinId(long skinId, Pageable pageable);

    @Query("SELECT p FROM PlayerRow p WHERE p.cape.id = :capeId")
    List<PlayerRow> findByCapeId(long capeId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE PlayerRow p SET p.submittedUuids = p.submittedUuids + :count WHERE p.id = :id")
    void incrementSubmittedUuids(@Param("id") UUID id, @Param("count") long count);
}