package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.persistence.mongo.SkinHistoryDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for skin history documents (collection "skin-history").
 */
public interface SkinHistoryRepository extends MongoRepository<SkinHistoryDocument, UUID> {

    @Query(value = "{ 'playerId' : ?0 }", sort = "{ 'lastUsed' : -1 }")
    List<SkinHistoryDocument> findByPlayerIdOrderByLastUsedDesc(UUID playerId);

    @Query("{ 'playerId' : ?0, 'skin' : ?1 }")
    Optional<SkinHistoryDocument> findFirstByPlayerIdAndSkinId(UUID playerId, UUID skinId);
}
