package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "username-history")
@CompoundIndex(name = "playerId_asc_timestamp_desc", def = "{ 'playerId' : 1, 'timestamp' : -1 }")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsernameHistoryDocument {
    /**
     * Mongo document id
     */
    @Id
    private UUID id;

    /**
     * The player this history entry belongs to.
     */
    private UUID playerId;

    /**
     * The username at this point in time.
     */
    private String username;

    /**
     * The time this username was seen on the player.
     */
    private Instant timestamp;
}
