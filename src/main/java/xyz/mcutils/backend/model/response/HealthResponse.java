package xyz.mcutils.backend.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The response for the health endpoint.
 */
@AllArgsConstructor
@Getter
public class HealthResponse {
    /**
     * The health status.
     */
    private final String status;
}
