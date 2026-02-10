package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerDocument {
    @Id
    private UUID id;

    @Indexed
    private String username;

    private boolean legacyAccount;

    @DocumentReference(lookup = "{ '_id' : ?#{#target} }")
    private SkinDocument skin;

    @ReadOnlyProperty
    @DocumentReference(lookup = "{ 'playerId' : ?#{#self._id} }", sort = "{ 'timestamp' : 1 }")
    private List<SkinHistoryDocument> skinHistory;

    @DocumentReference(lookup = "{ '_id' : ?#{#target} }")
    private CapeDocument cape;

    @ReadOnlyProperty
    @DocumentReference(lookup = "{ 'playerId' : ?#{#self._id} }", sort = "{ 'timestamp' : 1 }")
    private List<CapeHistoryDocument> capeHistory;

    private boolean hasOptifineCape;

    @Indexed
    private Date lastUpdated;

    private Date firstSeen;
}
