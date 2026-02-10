package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.Date;
import java.util.UUID;

@Document(collection = "cape-history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CapeHistoryDocument {

    @Id
    private UUID id;

    @Indexed
    private UUID playerId;

    @DocumentReference(lookup = "{ '_id' : ?#{#target} }")
    private CapeDocument cape;

    private Date timestamp;
}
