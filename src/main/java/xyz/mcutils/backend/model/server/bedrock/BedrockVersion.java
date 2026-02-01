package xyz.mcutils.backend.model.server.bedrock;

import lombok.NonNull;

/**
 * Version information for a server.
 *
 * @param protocol The protocol version of the server.
 * @param name     The version name of the server.
 */
public record BedrockVersion(int protocol, @NonNull String name) { }
