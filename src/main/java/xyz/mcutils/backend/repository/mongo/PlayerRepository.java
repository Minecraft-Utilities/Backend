package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Page;
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
    @Query(value = "{ 'username': ?0 }", fields = "{ '_id' : 1 }", collation = "{ 'locale' : 'en', 'strength' : 2 }")
    List<PlayerDocument> usernameToUuid(String username);

    /**
     * Search for players whose username starts with the given prefix, case-insensitive.
     *
     * @param prefix   the prefix to match (e.g. "steve" matches "Steve", "STEVE", "Steve_")
     * @param pageable used to limit the number of results (e.g. {@code PageRequest.of(0, limit)})
     * @return list of matching player documents, may be empty
     */
    List<PlayerDocument> findByUsernameStartingWithIgnoreCase(String prefix, Pageable pageable);

    /**
     * Find players whose last update was before the given date, ordered by lastUpdated ascending (stalest first).
     *
     * @param before   only return players with lastUpdated &lt; before
     * @param pageable page and size (e.g. first 1000)
     * @return page of player documents
     */
    Page<PlayerDocument> findByLastUpdatedBeforeOrderByLastUpdatedAsc(Date before, Pageable pageable);
}