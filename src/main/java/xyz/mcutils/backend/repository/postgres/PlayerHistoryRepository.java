package xyz.mcutils.backend.repository.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import xyz.mcutils.backend.model.persistence.postgres.PlayerHistoryRow;

public interface PlayerHistoryRepository extends JpaRepository<PlayerHistoryRow, PlayerHistoryRow.PlayerHistoryId> {
    @Query("SELECT COUNT(DISTINCT p.playerUuid) FROM PlayerHistoryRow p")
    long countDistinctPlayers();
}