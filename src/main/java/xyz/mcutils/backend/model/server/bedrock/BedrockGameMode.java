package xyz.mcutils.backend.model.server.bedrock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * The gamemode of a server.
 */
@AllArgsConstructor
@Getter
@ToString
public class BedrockGameMode {
    /**
     * The name of this gamemode.
     */
    @NonNull
    private final String name;

    /**
     * The numeric of this gamemode.
     */
    private final int numericId;
}