package xyz.mcutils.backend.skin;

import lombok.Getter;
import lombok.Setter;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory cache entry for a skin. Tracks dirty state and thread-safe accountsUsed for periodic flush to MongoDB.
 */
@Getter
public class CachedSkinDocument {
    private final SkinDocument document;
    private final AtomicLong accountsUsed;
    @Setter
    private volatile boolean dirty;

    public CachedSkinDocument(SkinDocument document) {
        this.document = document;
        this.dirty = false;
        this.accountsUsed = new AtomicLong(document.getAccountsUsed());
    }

    public void addAccountsUsed(long delta) {
        this.accountsUsed.addAndGet(delta);
        this.dirty = true;
    }

    public long getAccountsUsed() {
        return this.accountsUsed.get();
    }

    /**
     * Returns a copy of the document with the current in-memory accountsUsed (read-through snapshot).
     */
    public SkinDocument snapshotDocument() {
        SkinDocument doc = this.document;
        return SkinDocument.builder().id(doc.getId()).textureId(doc.getTextureId()).model(doc.getModel()).legacy(doc.isLegacy()).accountsUsed(this.accountsUsed.get()).firstPlayerSeenUsing(doc.getFirstPlayerSeenUsing()).firstSeen(doc.getFirstSeen()).build();
    }
}
