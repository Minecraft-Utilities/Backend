package xyz.mcutils.backend.model.dto.response;

/**
 * Response for the submit-players endpoint.
 *
 * @param enqueuedCount number of new player UUIDs added to the submit queue
 */
public record SubmitPlayersResponse(int enqueuedCount) {}
