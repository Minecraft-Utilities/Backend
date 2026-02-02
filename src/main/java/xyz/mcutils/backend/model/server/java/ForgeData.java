package xyz.mcutils.backend.model.server.java;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * @param channels          The list of mod channels on this server, null or empty if none.
 * @param mods              The list of mods on this server, null or empty if none.
 * @param truncated         Whether the mod list is truncated.
 * @param fmlNetworkVersion The version of the FML network.
 */
public record ForgeData(Channel[] channels, Mod[] mods, boolean truncated, int fmlNetworkVersion) {
    @AllArgsConstructor
    @Getter
    public static class Channel {
        /**
         * The id of this mod channel.
         */
        @NonNull
        @SerializedName("res")
        private final String name;

        /**
         * The version of this mod channel.
         */
        private final String version;

        /**
         * Whether this mod channel is required to join.
         */
        private boolean required;
    }

    /**
     * @param name    The id of this mod.
     * @param version The version of this mod.
     */
    public record Mod(@SerializedName("modId") @NonNull String name, @SerializedName("modmarker") String version) { }
}
