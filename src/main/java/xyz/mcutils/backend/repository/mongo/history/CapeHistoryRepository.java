package xyz.mcutils.backend.repository.mongo.history;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.player.history.CapeHistoryEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapeHistoryRepository extends MongoRepository<CapeHistoryEntry, UUID> {
    /**
     * Finds a cape for a player by its cape id
     *
     * @param playerId the uuid of the player
     * @param capeId the id of the cape
     * @return the cape entry
     */
    Optional<CapeHistoryEntry> findByCapeId(UUID playerId, String capeId);

    /**
     * Finds all cape history entries for a player
     *
     * @param playerId the uuid of the player
     * @return list of cape history entries
     */
    List<CapeHistoryEntry> findByPlayerId(UUID playerId);
}
