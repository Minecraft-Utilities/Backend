package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.Date;
import java.util.UUID;

/**
 * A single skin history entry: one player equipped one skin at a given time.
 * Stored in collection "skin-history"; referenced from PlayerDocument via @DocumentReference lookup by playerId.
 */
@Document(collection = "skin-history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkinHistoryDocument {

    @Id
    private UUID id;

    @Indexed
    private UUID playerId;

    @DocumentReference(lookup = "{ '_id' : ?#{#target} }")
    private SkinDocument skin;

    private Date timestamp;
}
