package xyz.mcutils.backend.repository.mongo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A repository for Player documents.
 *
 * @author Fascinated
 */
public interface PlayerRepository extends MongoRepository<PlayerDocument, UUID> {
    /**
     * Find players whose last update was before the given date, ordered by lastUpdated ascending (stalest first).
     *
     * @param before   only return players with lastUpdated before
     * @param pageable page and size (e.g. first 2500)
     * @return list of player documents (no total count query)
     */
    List<PlayerDocument> findListByLastUpdatedBeforeOrderByLastUpdatedAsc(Instant before, Pageable pageable);
}