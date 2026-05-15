package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.mcutils.backend.model.persistence.postgres.UsernameChangeEventRow;

import java.util.List;
import java.util.UUID;

public interface UsernameChangeEventRepository extends JpaRepository<UsernameChangeEventRow, Long> {
    List<UsernameChangeEventRow> findByPlayerIdOrderByTimestampDesc(UUID playerId);

    @Query("SELECT e FROM UsernameChangeEventRow e WHERE e.previousUsername IS NOT NULL ORDER BY e.timestamp DESC")
    List<UsernameChangeEventRow> findRecentNameChanges(Pageable pageable);
}
