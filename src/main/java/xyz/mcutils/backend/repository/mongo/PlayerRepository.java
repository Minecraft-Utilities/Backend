package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.player.Player;

import java.util.List;
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
}
