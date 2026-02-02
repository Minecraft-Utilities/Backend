package xyz.mcutils.backend.common;

import org.springframework.http.HttpStatusCode;

import java.util.List;

/**
 * @param endpoint        The endpoint.
 * @param allowedStatuses The statuses that indicate that the endpoint is online.
 */
public record Endpoint(String endpoint, List<HttpStatusCode> allowedStatuses) { }
