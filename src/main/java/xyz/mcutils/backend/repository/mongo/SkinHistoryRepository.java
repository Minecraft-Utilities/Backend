package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.persistence.mongo.SkinHistoryDocument;

import java.util.UUID;

/**
 * Repository for skin history documents (collection "skin-history").
 */
public interface SkinHistoryRepository extends MongoRepository<SkinHistoryDocument, UUID> {
}
