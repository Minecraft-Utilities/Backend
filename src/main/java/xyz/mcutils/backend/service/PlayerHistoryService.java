package xyz.mcutils.backend.service;

import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.persistence.mongo.*;
import xyz.mcutils.backend.repository.mongo.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.UsernameHistoryRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared service for ensuring player history is never missing.
 * Both create and refresh flows use these methods so the "current state always in history" rule
 * is enforced in one place.
 */
@Service
public class PlayerHistoryService {

    private final UsernameHistoryRepository usernameHistoryRepository;
    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;

    public PlayerHistoryService(UsernameHistoryRepository usernameHistoryRepository, SkinHistoryRepository skinHistoryRepository, CapeHistoryRepository capeHistoryRepository) {
        this.usernameHistoryRepository = usernameHistoryRepository;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
    }

    /**
     * Ensures the current username is present in username history (insert if missing).
     *
     * @param playerId the player id
     * @param username the current username to ensure
     * @param now      the timestamp
     */
    public void ensureUsernameInHistory(UUID playerId, String username, Instant now) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (usernameHistoryRepository.findFirstByPlayerIdAndUsername(playerId, username).isPresent()) {
            return;
        }
        usernameHistoryRepository.save(UsernameHistoryDocument.builder().id(UUID.randomUUID()).playerId(playerId).username(username).timestamp(now).build());
    }

    /**
     * Ensures (playerId, skinId) is in skin history; inserts if missing, updates lastUsed if present.
     *
     * @param playerId the player id
     * @param skinId   the skin id
     * @param now      the timestamp
     * @return true if a new entry was inserted, false if an existing entry was updated or already present
     */
    public boolean ensureSkinInHistory(UUID playerId, UUID skinId, Instant now) {
        if (playerId == null || skinId == null) {
            return false;
        }
        Optional<SkinHistoryDocument> existing = skinHistoryRepository.findFirstByPlayerIdAndSkinId(playerId, skinId);
        if (existing.isPresent()) {
            SkinHistoryDocument d = existing.get();
            d.setLastUsed(now);
            skinHistoryRepository.save(d);
            return false;
        }
        skinHistoryRepository.save(SkinHistoryDocument.builder().id(UUID.randomUUID()).playerId(playerId).skin(SkinDocument.builder().id(skinId).build()).lastUsed(now).timestamp(now).build());
        return true;
    }

    /**
     * Ensures (playerId, capeId) is in cape history; inserts if missing, updates lastUsed if present.
     *
     * @param playerId the player id
     * @param capeId   the cape id
     * @param now      the timestamp
     * @return true if a new entry was inserted, false if an existing entry was updated or already present
     */
    public boolean ensureCapeInHistory(UUID playerId, UUID capeId, Instant now) {
        if (playerId == null || capeId == null) {
            return false;
        }
        Optional<CapeHistoryDocument> existing = capeHistoryRepository.findFirstByPlayerIdAndCapeId(playerId, capeId);
        if (existing.isPresent()) {
            CapeHistoryDocument d = existing.get();
            d.setLastUsed(now);
            capeHistoryRepository.save(d);
            return false;
        }
        capeHistoryRepository.save(CapeHistoryDocument.builder().id(UUID.randomUUID()).playerId(playerId).cape(CapeDocument.builder().id(capeId).build()).lastUsed(now).timestamp(now).build());
        return true;
    }
}
