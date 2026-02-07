package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

/**
 * MongoDB document for vanilla capes. Contains only persisted fields;
 * derived data (part URLs, texture URLs) is built when constructing the domain VanillaCape.
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
    private String textureId;
}
