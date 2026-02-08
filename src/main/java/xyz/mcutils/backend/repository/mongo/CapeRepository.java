package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A repository for vanilla cape documents.
 *
 * @author Fascinated
 */
public interface CapeRepository extends MongoRepository<CapeDocument, UUID> {
    /**
     * Finds a cape document by its texture id.
     *
     * @param textureId the texture id
     * @return the cape document
     */
    @Query("{ textureId: ?0 }")
    Optional<CapeDocument> findByTextureId(String textureId);

    /**
     * Finds all cape documents ordered by the number of accounts that have this cape owned.
     *
     * @return the cape documents
     */
    List<CapeDocument> findAllByOrderByAccountsOwnedDesc();
}   