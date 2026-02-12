package xyz.mcutils.backend.model.dto.response;

import java.util.UUID;

/**
 * A page of skins for the /skins endpoint
 *
 * @param id the UUID of the cape
 * @param imageUrl the URL for the rendered skin image
 * @param accountsUsed the amount of accounts that have used this skin before
 */
public record SkinsResponse(
        UUID id,
        String imageUrl,
        long accountsUsed
) { }
