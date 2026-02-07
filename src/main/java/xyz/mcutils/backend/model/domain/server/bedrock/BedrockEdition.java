package xyz.mcutils.backend.model.domain.server.bedrock;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The edition of a Bedrock server.
 */
@AllArgsConstructor
@Getter
public enum BedrockEdition {
    /**
     * Minecraft: Pocket Edition.
     */
    MCPE,

    /**
     * Minecraft: Education Edition.
     */
    MCEE
}
