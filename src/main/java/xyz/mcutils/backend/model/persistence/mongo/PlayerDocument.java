package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.model.domain.player.Player;

import java.util.Date;
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
     * The UUID for the player's cape.
     */
    private UUID cape;

    /**
     * The time this account was last updated.
     */
    private Date lastUpdated;

    /**
     * The date this player was first seen on.
     */
    private Date firstSeen;
}
