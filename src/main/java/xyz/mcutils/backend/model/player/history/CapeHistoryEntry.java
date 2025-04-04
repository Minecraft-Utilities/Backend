package xyz.mcutils.backend.model.player.history;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class CapeHistoryEntry {
    /**
     * The ID of the cape.
     */
    private String id;

    /**
     * The timestamp this cape was first used.
     */
    private long firstUsed;

    /**
     * The timestamp this cape was last used.
     */
    private long lastUsed;
}
