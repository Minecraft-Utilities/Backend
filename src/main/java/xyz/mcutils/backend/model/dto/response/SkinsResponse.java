package xyz.mcutils.backend.model.dto.response;

/**
 * A page of skins for the /skins endpoint
 *
 * @param imageUrl the URL for the rendered skin image
 * @param accountsUsed the amount of accounts that have used this skin before
 */
public record SkinsResponse(
        String imageUrl,
        long accountsUsed
) { }
