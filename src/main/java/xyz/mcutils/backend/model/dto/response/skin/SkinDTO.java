package xyz.mcutils.backend.model.dto.response.skin;

import java.util.List;
import java.util.UUID;

/**
 * DTO for the /skin/:id route to see details about a specific skin.
 *
 * @param id the UUID of the cape
 * @param imageUrl the URL for the rendered skin image
 * @param accountsUsed the amount of accounts that have used this skin before
 * @param firstSeenUsing the name of the account first seen using this skin
 * @param accountsSeenUsing the first 100 accounts using this cape
 */
public record SkinDTO(
        UUID id,
        String imageUrl,
        long accountsUsed,
        String firstSeenUsing,
        List<String> accountsSeenUsing
) { }
