package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;

import java.util.Optional;

/**
 * A repository for vanilla cape documents.
 *
 * @author Fascinated
 */
public interface CapeRepository extends MongoRepository<CapeDocument, String> {
    /**
     * Finds a cape document by its texture id.
     *
     * @param textureId the texture id
     * @return the cape document
     */
    @Query("{ textureId: ?0 }")
    Optional<CapeDocument> findByTextureId(String textureId);
}