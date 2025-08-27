package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import xyz.mcutils.backend.model.player.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends MongoRepository<Player, UUID> {
    /**
     * Finds players that were last updated more than 24 hours ago,
     * ordered by lastUpdated (ascending) to get the oldest ones first.
     *
     * @param timestamp The timestamp representing 24 hours ago
     * @param pageable Pagination information
     * @return List of players sorted by lastUpdated (oldest first), limited to page size
     */
    @Query("{ 'lastUpdated': { $lt: ?0 } }")
    List<Player> findPlayersLastUpdatedBefore(long timestamp, Pageable pageable);

    /**
     * Finds a player by their username, ignoring case.
     *
     * @param username The username to search for
     * @return The player, or null if not found
     */
    Optional<Player> findByUsernameIgnoreCase(String username);

    /**
     * Gets the top contributors to the server.
     *
     * @param limit The number of contributors to return
     * @return A map of UUIDs to the number of contributions
     */
    @Query("{ 'uuidsContributed': { $gt: 0 } }")
    List<Player> findTopContributors(int limit);

    /**
     * Gets the number of players with the given cape ID.
     *
     * @param currentCapeId The ID of the cape
     * @return The number of players with the given cape ID
     */
    int countByCurrentCapeId(String currentCapeId);

    /**
     * Gets the most popular skin IDs by counting current usage directly in the database.
     * This aggregation does the counting and sorting on the database side.
     * 
     * @param limit maximum number of skins to return
     * @return list of skin IDs with their usage counts, ordered by popularity
     */
    @Aggregation(pipeline = {
        "{ $match: { currentSkinId: { $exists: true, $ne: null, $ne: '' } } }",
        "{ $group: { _id: '$currentSkinId', count: { $sum: 1 } } }",
        "{ $sort: { count: -1, _id: 1 } }",
        "{ $limit: ?0 }",
        "{ $project: { skinId: '$_id', currentUsageCount: '$count' } }"
    })
    List<Map<String, Object>> findMostPopularSkinIds(int limit);
}
