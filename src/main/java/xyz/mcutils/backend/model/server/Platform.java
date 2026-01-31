package xyz.mcutils.backend.model.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import xyz.mcutils.backend.service.pinger.MinecraftServerPinger;
import xyz.mcutils.backend.service.pinger.impl.BedrockMinecraftServerPinger;
import xyz.mcutils.backend.service.pinger.impl.JavaMinecraftServerPinger;

/**
 * A platform a Minecraft
 * server can operate on.
 */
@AllArgsConstructor
@Getter
public enum Platform {
    /**
     * The Java edition of Minecraft.
     */
    JAVA(new JavaMinecraftServerPinger(), 25565),

    /**
     * The Bedrock edition of Minecraft.
     */
    BEDROCK(new BedrockMinecraftServerPinger(), 19132);

    /**
     * The server pinger for this platform.
     */
    @NonNull
    private final MinecraftServerPinger<?> pinger;

    /**
     * The default server port for this platform.
     */
    private final int defaultPort;
}