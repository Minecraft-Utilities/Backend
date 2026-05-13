package xyz.mcutils.backend.cape;

import lombok.Getter;
import lombok.Setter;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory cache entry for a cape. Tracks dirty state and thread-safe accountsOwned for periodic flush to MongoDB.
 */
@Getter
public class CachedCapeDocument {
    private final CapeDocument document;
    private final AtomicLong accountsOwned;
    @Setter
    private volatile boolean dirty;

    public CachedCapeDocument(CapeDocument document) {
        this.document = document;
        this.dirty = false;
        this.accountsOwned = new AtomicLong(document.getAccountsOwned());
    }

    public void addAccountsOwned(long delta) {
        this.accountsOwned.addAndGet(delta);
        this.dirty = true;
    }

    public long getAccountsOwned() {
        return this.accountsOwned.get();
    }

    /**
     * Returns a copy of the document with the current in-memory accountsOwned (read-through snapshot).
     */
    public CapeDocument snapshotDocument() {
        CapeDocument doc = this.document;
        return CapeDocument.builder().id(doc.getId()).name(doc.getName()).textureId(doc.getTextureId()).accountsOwned(this.accountsOwned.get()).firstSeen(doc.getFirstSeen()).firstPlayerSeenUsing(doc.getFirstPlayerSeenUsing()).build();
    }
}
