package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.UUID;

/**
 * MongoDB document for vanilla capes.
 * Contains only persisted fields
 *
 * @author Fascinated
 */
@Document(collection = "capes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CapeDocument {
    /**
     * Mongo document id
     */
    @Id
    private UUID id;

    /**
     * Display name of the cape.
     */
    private String name;

    /**
     * Texture id (SHA-1 hash).
     */
    @Indexed(unique = true)
    private String textureId;

    /**
     * The number of accounts that have this cape owned.
     */
    private long accountsOwned;

    /**
     * The date this cape was first seen on.
     */
    private Date firstSeen;
}
