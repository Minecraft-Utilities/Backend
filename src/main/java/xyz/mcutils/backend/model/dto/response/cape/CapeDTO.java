package xyz.mcutils.backend.model.dto.response.cape;

import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.persistence.mongo.CapeDocument;

import java.util.List;
import java.util.UUID;

/**
 * DTO for the /capes/:id route to see details about a specific cape.
 *
 * @param id                 the UUID of the cape
 * @param textureId          the mojang texture id for this cape
 * @param name               the display name of this cape
 * @param imageUrl           the URL for the rendered cape image
 * @param accountsOwned      the amount of accounts that own this cape
 * @param accountsSeenOwning the accounts that currently own this cape
 */
public record CapeDTO(UUID id, String textureId, String name, String imageUrl, long accountsOwned,
                      List<String> accountsSeenOwning) {
    public static CapeDTO fromDocument(CapeDocument document, List<String> accountsSeenOwning) {
        return new CapeDTO(
                document.getId(),
                document.getTextureId(),
                document.getName(),
                "%s/capes/vanilla/%s/front.png".formatted(AppConfig.INSTANCE.getWebPublicUrl(), document.getTextureId()),
                document.getAccountsOwned(),
                accountsSeenOwning
        );
    }
}
