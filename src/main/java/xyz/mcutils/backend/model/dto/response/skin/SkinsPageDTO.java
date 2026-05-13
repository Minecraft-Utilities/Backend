package xyz.mcutils.backend.model.dto.response.skin;

import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.persistence.mongo.SkinDocument;

import java.util.UUID;

/**
 * A page of skins for the /skins endpoint
 *
 * @param id           the UUID of the cape
 * @param imageUrl     the URL for the rendered skin image
 * @param accountsUsed the amount of accounts that have used this skin before
 */
public record SkinsPageDTO(UUID id, String imageUrl, long accountsUsed) {
    public static SkinsPageDTO fromDocument(SkinDocument document) {
        return new SkinsPageDTO(
                document.getId(),
                "%s/skins/%s/fullbody_iso_front.png".formatted(AppConfig.INSTANCE.getWebPublicUrl(), document.getTextureId()),
                document.getAccountsUsed()
        );
    }
}
