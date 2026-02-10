package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.persistence.mongo.CapeHistoryDocument;

import java.util.UUID;

/**
 * Repository for cape history documents (collection "cape-history").
 */
public interface CapeHistoryRepository extends MongoRepository<CapeHistoryDocument, UUID> {
}
