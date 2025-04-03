package xyz.mcutils.backend.model.player.history;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.mcutils.backend.model.skin.Skin;

@NoArgsConstructor
@Getter @Setter
public class SkinHistoryEntry {
    /**
     * The ID of the skin.
     */
    private String id;

    /**
     * The legacy status of the skin
     */
    private boolean legacy;

    /**
     * The model for the skin.
     */
    private Skin.Model model;

    /**
     * The timestamp this skin was first used.
     */
    private long firstUsed;

    /**
     * The timestamp this skin was last used.
     */
    private long lastUsed;

    public SkinHistoryEntry(String id, boolean legacy, Skin.Model model, long firstUsed, long lastUsed) {
        this.id = id;
        this.legacy = legacy;
        this.model = model;
        this.firstUsed = firstUsed;
        this.lastUsed = lastUsed;
    }
}
