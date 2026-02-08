package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Finds a skin document by its texture id.
     *
     * @param textureId the texture id
     * @return the skin document, if present
     */
    @Query("{ textureId: ?0 }")
    Optional<SkinDocument> findByTextureId(String textureId);

    /**
     * Finds all skin documents ordered by the number of accounts that have used this skin (descending),
     * then by id (ascending) for stable ordering when counts tie.
     *
     * @param pageable pagination (page index and size) and optional sort
     * @return a page of skin documents
     */
    Page<SkinDocument> findAllByOrderByAccountsUsedDescIdAsc(Pageable pageable);
}