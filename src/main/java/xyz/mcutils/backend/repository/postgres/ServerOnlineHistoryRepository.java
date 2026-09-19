package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.mcutils.backend.model.persistence.postgres.ServerOnlineHistoryRow;

public interface ServerOnlineHistoryRepository extends JpaRepository<ServerOnlineHistoryRow, ServerOnlineHistoryRow.ServerOnlineHistoryId> {
}