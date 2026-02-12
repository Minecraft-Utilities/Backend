package xyz.mcutils.backend.model.dto.response.skin;

import java.util.UUID;

/**
 * A page of skins for the /skins endpoint
 *
 * @param id the UUID of the cape
 * @param imageUrl the URL for the rendered skin image
 * @param accountsUsed the amount of accounts that have used this skin before
 */
public record SkinsPageDTO(
        UUID id,
        String imageUrl,
        long accountsUsed
) { }
