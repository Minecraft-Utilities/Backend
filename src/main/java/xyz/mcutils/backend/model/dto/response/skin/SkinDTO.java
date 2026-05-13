package xyz.mcutils.backend.model.dto.response.skin;

import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;

import java.util.List;
import java.util.UUID;

/**
 * DTO for the /skins/:id route to see details about a specific skin.
 *
 * @param id                the UUID of the skin
 * @param textureId         the mojang texture id for this skin
 * @param imageUrl          the URL for the rendered skin image
 * @param accountsUsed      the amount of accounts that have used this skin before
 * @param firstSeenUsing    the name of the account first seen using this skin
 * @param accountsSeenUsing the accounts using this cape
 */
public record SkinDTO(UUID id, String textureId, String imageUrl, long accountsUsed, String firstSeenUsing,
                      List<String> accountsSeenUsing) {
    public static SkinDTO fromDocument(SkinDocument document, String firstSeenUsing, List<String> accountsSeenUsing) {
        return new SkinDTO(
                document.getId(),
                document.getTextureId(),
                "%s/skins/%s/fullbody_iso_front.png".formatted(AppConfig.INSTANCE.getWebPublicUrl(), document.getTextureId()),
                document.getAccountsUsed(),
                firstSeenUsing,
                accountsSeenUsing
        );
    }
}
