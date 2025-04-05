package xyz.mcutils.backend.model.player.history;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class SkinHistoryEntry {
    /**
     * The ID of the skin.
     */
    private String id;

    /**
     * The timestamp this skin was first used.
     */
    private long firstUsed;

    /**
     * The timestamp this skin was last used.
     */
    private long lastUsed;
}
