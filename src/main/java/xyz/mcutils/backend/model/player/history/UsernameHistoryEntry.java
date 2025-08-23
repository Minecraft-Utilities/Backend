package xyz.mcutils.backend.model.player.history;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Document("username-history")
public class UsernameHistoryEntry {
    /**
    * The ID of this entry.
    */
   @Id
   private UUID id;

    /**
     * The ID of the player who this entry belongs to.
     */
    @Indexed
    private UUID playerId;

    /**
     * The username of the player.
     */
    @Indexed
    private String username;

    /**
     * The timestamp this entry was created.
     * the timestamp it occurred, otherwise -1 if unknown
     */
    @Setter
    private long timestamp;
}
