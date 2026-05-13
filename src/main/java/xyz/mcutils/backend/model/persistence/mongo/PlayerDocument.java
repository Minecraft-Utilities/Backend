package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import xyz.mcutils.backend.model.domain.player.Player;

import java.time.Instant;
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
    @Indexed(name = "submittedUuids_desc")
    private long submittedUuids;

    /**
     * The player's current skin id.
     */
    @Indexed(name = "skin")
    @Field("skin")
    private UUID skinId;

    /**
     * The player's current cape id.
     */
    @Field("cape")
    private UUID capeId;

    /**
     * The time this account was last updated.
     */
    @Indexed
    private Instant lastUpdated;

    /**
     * The date this player was first seen on.
     */
    private Instant firstSeen;
}
