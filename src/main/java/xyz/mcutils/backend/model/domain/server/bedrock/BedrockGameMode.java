package xyz.mcutils.backend.model.domain.server.bedrock;

import lombok.NonNull;

/**
 * The gamemode of a server.
 *
 * @param name      The name of this gamemode.
 * @param numericId The numeric of this gamemode.
 */
public record BedrockGameMode(@NonNull String name, int numericId) { }
