package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * A repository for Player documents.
 *
 * @author Fascinated
 */
public interface PlayerRepository extends MongoRepository<PlayerDocument, UUID> {
    /**
     * Lookup a player by their username, case-insensitive.
     * Only returns id (other fields not loaded).
     *
     * @param username the player's username
     * @return list of matching player documents, may be empty
     */
    @Query(value = "{ 'username': ?0 }", collation = "{ 'locale' : 'en', 'strength' : 2 }", hint = "username_case_insensitive")
    List<PlayerDocument> usernameToUuid(String username);

    /**
     * Find players whose last update was before the given date, ordered by lastUpdated ascending (stalest first).
     *
     * @param before   only return players with lastUpdated before
     * @param pageable page and size (e.g. first 2500)
     * @return list of player documents (no total count query)
     */
    List<PlayerDocument> findListByLastUpdatedBeforeOrderByLastUpdatedAsc(Date before, Pageable pageable);
}