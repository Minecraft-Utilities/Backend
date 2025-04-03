package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * A repository for {@link Player}s.
 *
 * @author Fascinated
 */
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
}
