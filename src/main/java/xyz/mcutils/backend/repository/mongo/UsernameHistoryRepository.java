package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import xyz.mcutils.backend.model.persistence.mongo.UsernameHistoryDocument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for username history documents (collection "username-history").
 */
public interface UsernameHistoryRepository extends MongoRepository<UsernameHistoryDocument, UUID> {

    @Query(value = "{ 'playerId' : ?0 }", sort = "{ 'timestamp' : -1 }")
    List<UsernameHistoryDocument> findByPlayerIdOrderByTimestampDesc(UUID playerId);

    Optional<UsernameHistoryDocument> findFirstByPlayerIdAndUsername(UUID playerId, String username);
}
