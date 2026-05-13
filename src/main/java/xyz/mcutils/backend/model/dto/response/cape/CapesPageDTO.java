package xyz.mcutils.backend.model.dto.response.cape;

import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;

import java.util.UUID;

/**
 * A page of capes for the /capes endpoint
 *
 * @param id           the UUID of the cape
 * @param imageUrl     the URL for the rendered cape image
 * @param accountsOwned the amount of accounts that own this cape
 */
public record CapesPageDTO(UUID id, String imageUrl, long accountsOwned) {
    public static CapesPageDTO fromDocument(CapeDocument document) {
        return new CapesPageDTO(
                document.getId(),
                "%s/capes/vanilla/%s/front.png".formatted(AppConfig.INSTANCE.getWebPublicUrl(), document.getTextureId()),
                document.getAccountsOwned()
        );
    }
}
