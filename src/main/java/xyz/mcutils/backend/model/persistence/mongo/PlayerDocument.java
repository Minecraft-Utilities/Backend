package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import xyz.mcutils.backend.model.domain.player.Player;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB document for {@link Player}'s.
 *
 * @author Fascinated
 */
@Document(collection = "players")
@CompoundIndex(name = "lastUpdated_asc_id_asc", def = "{ 'lastUpdated' : 1, '_id' : 1 }")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerDocument {
    /**
     * Mongo document id.
     */
    @Id
    private UUID id;

    /**
     * The username for the player.
     */
    private String username;

    /**
     * Is this a legacy account?
     */
    private boolean legacyAccount;

    /**
     * The amount of new uuids this player has submitted.
     */
    private long submittedUuids;

    /**
     * The player's current skin.
     */
    @Indexed(name = "skin")
    @DocumentReference
    private SkinDocument skin;

    /**
     * The player's current cape.
     */
    @Indexed(name = "cape")
    @DocumentReference
    private CapeDocument cape;

    /**
     * The skins this player has previously equipped (including current).
     */
    @ReadOnlyProperty
    @DocumentReference(lookup = "{ 'playerId' : ?#{#self._id} }", sort = "{ 'lastUsed' : -1 }", lazy = true)
    private List<SkinHistoryDocument> skinHistory;

    /**
     * The usernames this player has previously used (including current).
     */
    @ReadOnlyProperty
    @DocumentReference(lookup = "{ 'playerId' : ?#{#self._id} }", sort = "{ 'timestamp' : -1 }", lazy = true)
    private List<UsernameHistoryDocument> usernameHistory;

    /**
     * The capes this player has previously equipped (including current).
     */
    @ReadOnlyProperty
    @DocumentReference(lookup = "{ 'playerId' : ?#{#self._id} }", sort = "{ 'lastUsed' : -1 }", lazy = true)
    private List<CapeHistoryDocument> capeHistory;

    /**
     * The time this account was last updated.
     */
    @Indexed
    private Date lastUpdated;

    /**
     * The date this player was first seen on.
     */
    private Date firstSeen;
}
