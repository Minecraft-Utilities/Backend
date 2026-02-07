package xyz.mcutils.backend.model.dto.response;

/**
 * The response for the index endpoint.
 *
 * @param app     The application name.
 * @param version The application version.
 * @param docs    The URL to the API documentation.
 */
public record IndexResponse(String app, String version, String docs) { }
