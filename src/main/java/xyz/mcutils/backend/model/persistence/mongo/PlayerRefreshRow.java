package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.UUID;

/**
 * Minimal projection of a player document for the refresh chunk.
 * Used to load only id, username, skin id, cape id, lastUpdated, legacyAccount
 * without resolving {@link org.springframework.data.mongodb.core.mapping.DocumentReference}s.
 */
@Document(collection = "players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerRefreshRow {
    @Id
    private UUID id;

    private String username;

    /**
     * Skin reference (stored as id in collection).
     */
    @Field("skin")
    private UUID skin;

    /**
     * Cape reference (stored as id in collection).
     */
    @Field("cape")
    private UUID cape;

    private Date lastUpdated;

    private boolean legacyAccount;
}
