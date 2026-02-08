package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;

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
}