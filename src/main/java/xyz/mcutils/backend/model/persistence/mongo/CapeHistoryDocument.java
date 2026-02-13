package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.Date;
import java.util.UUID;

@Document(collection = "cape-history")
@CompoundIndex(name = "playerId_cape_unique", def = "{ 'playerId' : 1, 'cape' : 1 }", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CapeHistoryDocument {
    /**
     * Mongo document id.
     */
    @Id
    private UUID id;

    /**
     * The player this history entry belongs to.
     */
    private UUID playerId;

    /**
     * The cape that was equipped.
     */
    @DocumentReference(lookup = "{ '_id' : ?#{#target} }")
    private CapeDocument cape;

    /**
     * The time this cape was last used by the player.
     */
    @Indexed
    private Date lastUsed;

    /**
     * The time this cape was seen on the player.
     */
    private Date timestamp;
}
