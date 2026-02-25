package xyz.mcutils.backend.model.dto;

import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;

import java.util.UUID;

/**
 * One player to create from a Mojang profile; optional submitter for submit-queue flow.
 * Used when calling {@link xyz.mcutils.backend.service.PlayerService#createPlayers(java.util.List)}.
 *
 * @param profile    the Mojang profile token for the player to create
 * @param submittedBy the UUID of the player who submitted this one, or null if not from submit queue
 */
public record PlayerCreateSubmission(MojangProfileToken profile, UUID submittedBy) {

    /**
     * Convenience constructor for a submission with no submitter (submittedBy = null).
     *
     * @param profile the Mojang profile token for the player to create
     */
    public PlayerCreateSubmission(MojangProfileToken profile) {
        this(profile, null);
    }
}
