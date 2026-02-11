package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.persistence.mongo.UsernameHistoryDocument;

import java.util.UUID;

/**
 * Repository for username history documents (collection "username-history").
 */
public interface UsernameHistoryRepository extends MongoRepository<UsernameHistoryDocument, UUID> {
}
