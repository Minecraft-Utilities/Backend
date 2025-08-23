package xyz.mcutils.backend.repository.mongo.history;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.player.history.SkinHistoryEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkinHistoryRepository extends MongoRepository<SkinHistoryEntry, UUID> {
    /**
     * Finds a skin for a player by its skin id
     *
     * @param playerId the uuid of the player
     * @param skinId the id of the skin
     * @return the skin entry
     */
    Optional<SkinHistoryEntry> findByPlayerIdAndSkinId(UUID playerId, String skinId);

    /**
     * Finds all skin history entries for a player
     *
     * @param playerId the uuid of the player
     * @return list of skin history entries
     */
    List<SkinHistoryEntry> findByPlayerId(UUID playerId);
}
