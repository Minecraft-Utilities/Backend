package xyz.mcutils.backend.model.player.history;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
public class UsernameHistoryEntry {
    /**
     * The username of the player.
     */
    private String username;

    /**
     * The timestamp this entry was created.
     * the timestamp it occurred, otherwise -1 if unknown
     */
    @Setter
    private long timestamp;

    public UsernameHistoryEntry(String username, long timestamp) {
        this.username = username;
        this.timestamp = timestamp;
    }
}
