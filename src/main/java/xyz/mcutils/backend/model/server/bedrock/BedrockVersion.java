package xyz.mcutils.backend.model.server.bedrock;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Version information for a server.
 */
@AllArgsConstructor
@Getter
@ToString
public class BedrockVersion {
    /**
     * The protocol version of the server.
     */
    private final int protocol;

    /**
     * The version name of the server.
     */
    @NonNull
    private final String name;
}
