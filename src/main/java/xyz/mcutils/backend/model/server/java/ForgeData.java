package xyz.mcutils.backend.model.server.java;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@AllArgsConstructor
@Getter
public class ForgeData {
    /**
     * The list of mod channels on this server, null or empty if none.
     */
    private final Channel[] channels;

    /**
     * The list of mods on this server, null or empty if none.
     */
    private final Mod[] mods;

    /**
     * Whether the mod list is truncated.
     */
    private final boolean truncated;

    /**
     * The version of the FML network.
     */
    private final int fmlNetworkVersion;

    @AllArgsConstructor @Getter
    public static class Channel {
        /**
         * The id of this mod channel.
         */
        @NonNull
        @SerializedName("res") private final String name;

        /**
         * The version of this mod channel.
         */
        private final String version;

        /**
         * Whether this mod channel is required to join.
         */
        private boolean required;
    }

    @AllArgsConstructor @Getter
    public static class Mod {
        /**
         * The id of this mod.
         */
        @NonNull @SerializedName("modId") private final String name;

        /**
         * The version of this mod.
         */
        @SerializedName("modmarker") private final String version;
    }
}
