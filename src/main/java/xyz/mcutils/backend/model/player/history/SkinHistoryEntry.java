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
@Getter @Setter
@Document("skin-history")
public class SkinHistoryEntry {
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
     * The ID of the skin.
     */
    @Indexed
    private String skinId;

    /**
     * The timestamp this skin was first seen by McUtils.
     */
    private long firstSeen;

    /**
     * The timestamp this skin was last used.
     */
    private long lastUsed;
}
