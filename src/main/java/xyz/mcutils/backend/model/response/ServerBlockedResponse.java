package xyz.mcutils.backend.model.response;

/**
 * The response for the server blocked status endpoint.
 *
 * @param blocked Whether the server is blocked.
 */
public record ServerBlockedResponse(boolean blocked) { }
