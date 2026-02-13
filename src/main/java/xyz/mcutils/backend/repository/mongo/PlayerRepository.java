package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A repository for Player documents.
 *
 * @author Fascinated
 */
public interface PlayerRepository extends MongoRepository<PlayerDocument, UUID> {
    /**
     * Gets a player by their username.
     *
     * @param username the player's username
     * @return the player
     */
    Optional<PlayerDocument> findByUsername(String username);

    /**
     * Lookup a player by their username, case-insensitive.
     * Only returns id (other fields not loaded).
     *
     * @param username the player's username
     * @return list of matching player documents, may be empty
     */
    @Query(value = "{ 'username': ?0 }", fields = "{ '_id' : 1 }", collation = "{ 'locale' : 'en', 'strength' : 2 }", hint = "username_case_insensitive")
    List<PlayerDocument> usernameToUuid(String username);

    /**
     * Search for players whose username is in the range [prefixInclusive, prefixEndExclusive), case-insensitive.
     * Uses a range scan on the case-insensitive index (fast). Caller must pass prefixEndExclusive as the
     * lexicographically smallest string greater than any username starting with the prefix (e.g. "wildd" → "wilde").
     *
     * @param prefixInclusive   lower bound (inclusive), e.g. "wildd"
     * @param prefixEndExclusive upper bound (exclusive), e.g. "wilde"
     * @param pageable         page and size
     * @return list of matching player documents, may be empty
     */
    @Query(value = "{ 'username': { $gte: ?0, $lt: ?1 } }", collation = "{ 'locale' : 'en', 'strength' : 2 }", hint = "username_case_insensitive")
    List<PlayerDocument> findByUsernameStartingWithIgnoreCase(String prefixInclusive, String prefixEndExclusive, Pageable pageable);

    /**
     * Find players whose last update was before the given date, ordered by lastUpdated ascending (stalest first).
     *
     * @param before   only return players with lastUpdated before
     * @param pageable page and size (e.g. first 2500)
     * @return list of player documents (no total count query)
     */
    List<PlayerDocument> findListByLastUpdatedBeforeOrderByLastUpdatedAsc(Date before, Pageable pageable);


    /**
     * Find players by their current skin id.
     *
     * @param skinId   the skin document id
     * @param pageable page and size (e.g. first 100)
     * @return list of player documents
     */
    @Query("{ 'skin': ?0 }")
    List<PlayerDocument> findBySkinId(UUID skinId, Pageable pageable);
}