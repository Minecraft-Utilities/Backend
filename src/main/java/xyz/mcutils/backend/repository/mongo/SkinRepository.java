package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;

import java.util.Optional;
import java.util.UUID;

/**
 * A repository for Skin documents.
 *
 * @author Fascinated
 */
public interface SkinRepository extends MongoRepository<SkinDocument, UUID> {
    /**
     * Finds a cape document by its texture id.
     *
     * @param textureId the texture id
     * @return the cape document
     */
    @Query("{ textureId: ?0 }")
    Optional<SkinDocument> findByTextureId(String textureId);
}