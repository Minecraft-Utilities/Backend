package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.UUID;

@Document(collection = "username-history")
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
    @Indexed
    private UUID playerId;

    /**
     * The username at this point in time.
     */
    private String username;

    /**
     * The time this username was seen on the player.
     */
    private Date timestamp;
}
