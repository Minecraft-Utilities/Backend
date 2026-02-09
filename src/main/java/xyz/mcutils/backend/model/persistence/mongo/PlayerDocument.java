package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.model.domain.player.Player;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB document for {@link Player}'s.
 * Contains only persisted fields
 *
 * @author Fascinated
 */
@Document(collection = "players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerDocument {
    /**
     * Mongo document id
     */
    @Id
    private UUID id;

    /**
     * The username for the player.
     */
    @Indexed
    private String username;

    /**
     * Is this a legacy account?
     */
    private boolean legacyAccount;

    /**
     * The UUID for the player's skin.
     */
    private UUID skin;

    /**
     * The skins this player has previously equipped (including current).
     */
    private List<HistoryItem> skinHistory;

    /**
     * The UUID for the player's cape.
     */
    private UUID cape;

    /**
     * The capes this player has previously equipped (including current).
     */
    private List<HistoryItem> capeHistory;

    /**
     * Does this player have an Optifine cape equipped?
     */
    private boolean hasOptifineCape;

    /**
     * The time this account was last updated.
     */
    @Indexed
    private Date lastUpdated;

    /**
     * The date this player was first seen on.
     */
    private Date firstSeen;


    /**
     * A history item used for Skins and Capes.
     *
     * @param uuid the uuid of the skin or cape
     * @param timestamp the timestamp that this change was seen
     */
    public record HistoryItem(UUID uuid, Date timestamp) { }
}
