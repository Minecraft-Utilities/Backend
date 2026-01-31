package xyz.mcutils.backend.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The response for the index endpoint.
 */
@AllArgsConstructor
@Getter
public class IndexResponse {
    /**
     * The application name.
     */
    private final String app;

    /**
     * The application version.
     */
    private final String version;

    /**
     * The URL to the API documentation.
     */
    private final String docs;
}
