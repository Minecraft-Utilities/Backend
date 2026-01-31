package xyz.mcutils.backend.model.server.java;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Forge mod information for a server.
 */
@AllArgsConstructor
@Getter
@ToString
public class ForgeModInfo {
    /**
     * The type of modded server this is.
     */
    @NonNull
    private final String type;

    /**
     * The list of mods on this server, null or empty if none.
     */
    private final ForgeMod[] modList;

    /**
     * A forge mod for a server.
     */
    @AllArgsConstructor @Getter @ToString
    private static class ForgeMod {
        /**
         * The id of this mod.
         */
        @NonNull @SerializedName("modid") private final String name;

        /**
         * The version of this mod.
         */
        private final String version;
    }
}
