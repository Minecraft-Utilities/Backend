package xyz.mcutils.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for submitting players to the submit queue.
 * Each element is a UUID (with or without dashes).
 *
 * @param players     list of player UUIDs
 * @param submittedBy optional uuid of the player who submitted them
 */
public record SubmitPlayersRequest(
        @NotEmpty(message = "players must not be empty")
        @Size(min = 1, max = 5000, message = "players must contain between 1 and 5000 UUIDs")
        List<@NotBlank(message = "each player identifier must not be blank") String> players,
        String submittedBy
) {}
