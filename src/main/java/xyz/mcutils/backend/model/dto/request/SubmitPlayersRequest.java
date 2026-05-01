package xyz.mcutils.backend.model.dto.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for submitting players to the submit queue.
 * Each element is a UUID (with or without dashes).
 *
 * @param uuids       list of player UUIDs
 * @param submittedBy optional uuid of the player who submitted them (may be null or omitted)
 */
public record SubmitPlayersRequest(@NotEmpty @Size(min = 1, max = 2_000) List<@NotBlank String> uuids,
                                   @Nullable String submittedBy) {}
