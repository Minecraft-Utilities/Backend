package xyz.mcutils.backend.model.persistence.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import xyz.mcutils.backend.model.domain.skin.Skin;

import java.util.Date;
import java.util.UUID;

/**
 * MongoDB document for Skins.
 * Contains only persisted fields
 *
 * @author Fascinated
 */
@Document(collection = "skins")
@CompoundIndex(name = "accountsUsed_desc_id_asc", def = "{ 'accountsUsed' : -1, '_id' : 1 }")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkinDocument {
    /**
     * Mongo document id
     */
    @Id
    private UUID id;

    /**
     * Texture id (SHA-1 hash).
     */
    @Indexed(unique = true)
    private String textureId;

    /**
     * The skins model format.
     */
    private Skin.Model model;

    /**
     * Whether this skin is in the legacy format.
     */
    private boolean legacy;

    /**
     * The number of accounts that have used this
     */
    private long accountsUsed;

    /**
     * The first player seen using this cape.
     */
    private UUID firstPlayerSeenUsing;

    /**
     * The date this skin was first seen on.
     */
    private Date firstSeen;
}
