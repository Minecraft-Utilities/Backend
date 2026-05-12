package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "skin-history")
@CompoundIndex(name = "playerId_asc_lastUsed_desc", def = "{ 'playerId' : 1, 'lastUsed' : -1 }")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkinHistoryDocument {
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
     * The skin that was equipped
     */
    @DocumentReference(lookup = "{ '_id' : ?#{#target} }")
    private SkinDocument skin;

    /**
     * The time this skin was last used by the player.
     */
    private Instant lastUsed;

    /**
     * The time this skin was first seen on the player.
     */
    private Instant timestamp;
}
