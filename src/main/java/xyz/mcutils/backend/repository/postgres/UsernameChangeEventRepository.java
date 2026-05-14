package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.UsernameChangeEventRow;

import java.util.List;
import java.util.UUID;

public interface UsernameChangeEventRepository extends JpaRepository<UsernameChangeEventRow, Long> {
    List<UsernameChangeEventRow> findByPlayerIdOrderByTimestampDesc(UUID playerId);
}
