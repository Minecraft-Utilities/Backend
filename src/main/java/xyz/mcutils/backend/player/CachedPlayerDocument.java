package xyz.mcutils.backend.player;

import lombok.Getter;
import lombok.Setter;
import xyz.mcutils.backend.model.persistence.mongo.PlayerDocument;

/**
 * In-memory cache entry for a player. Tracks dirty state for periodic flush to MongoDB.
 */
@Getter
public class CachedPlayerDocument {
    private final PlayerDocument document;

    @Setter
    private volatile boolean dirty;

    public CachedPlayerDocument(PlayerDocument document) {
        this.document = document;
        this.dirty = false;
    }

    public void addSubmittedUuids(long delta) {
        this.document.setSubmittedUuids(this.document.getSubmittedUuids() + delta);
        this.dirty = true;
    }
}
