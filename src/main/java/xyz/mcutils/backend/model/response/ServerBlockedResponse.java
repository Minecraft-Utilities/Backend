package xyz.mcutils.backend.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The response for the server blocked status endpoint.
 */
@AllArgsConstructor
@Getter
public class ServerBlockedResponse {
    /**
     * Whether the server is blocked.
     */
    private final boolean blocked;
}
