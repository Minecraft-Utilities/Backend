package xyz.mcutils.backend.model.domain.server.java;

import com.google.gson.annotations.SerializedName;
import lombok.NonNull;

/**
 * @param channels          The list of mod channels on this server, null or empty if none.
 * @param mods              The list of mods on this server, null or empty if none.
 * @param truncated         Whether the mod list is truncated.
 * @param fmlNetworkVersion The version of the FML network.
 */
public record ForgeData(Channel[] channels, Mod[] mods, boolean truncated, int fmlNetworkVersion) {
    /**
     * Mod channel on a Forge server.
     *
     * @param name     The id of this mod channel.
     * @param version  The version of this mod channel.
     * @param required Whether this mod channel is required to join.
     */
    public record Channel(
            @SerializedName("res") @NonNull String name,
            String version,
            boolean required
    ) { }

    /**
     * @param name    The id of this mod.
     * @param version The version of this mod.
     */
    public record Mod(@SerializedName("modId") @NonNull String name, @SerializedName("modmarker") String version) { }
}
