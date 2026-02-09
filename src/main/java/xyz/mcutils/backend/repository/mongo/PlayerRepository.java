package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;

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
}