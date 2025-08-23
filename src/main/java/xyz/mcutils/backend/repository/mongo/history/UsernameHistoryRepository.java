package xyz.mcutils.backend.repository.mongo.history;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.player.history.UsernameHistoryEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsernameHistoryRepository extends MongoRepository<UsernameHistoryEntry, UUID> {
    /**
     * Finds a username entry for a player by its username id
     *
     * @param playerId the uuid of the player
     * @param username the username
     * @return the username entry
     */
    Optional<UsernameHistoryEntry> findByPlayerIdAndUsername(UUID playerId, String username);

    /**
     * Finds all username history entries for a player
     *
     * @param playerId the uuid of the player
     * @return list of username history entries
     */
    List<UsernameHistoryEntry> findByPlayerId(UUID playerId);
}
