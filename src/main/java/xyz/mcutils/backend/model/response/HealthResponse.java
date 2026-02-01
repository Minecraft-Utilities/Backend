package xyz.mcutils.backend.model.response;

import lombok.AllArgsConstructor;

/**
 * The response for the health endpoint.
 *
 * @param status The health status.
 */
public record HealthResponse(String status) { }
