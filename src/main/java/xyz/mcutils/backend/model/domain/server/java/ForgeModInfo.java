package xyz.mcutils.backend.model.domain.server.java;

import com.google.gson.annotations.SerializedName;
import lombok.NonNull;

/**
 * Forge mod information for a server.
 *
 * @param type    The type of modded server this is.
 * @param modList The list of mods on this server, null or empty if none.
 */
public record ForgeModInfo(@NonNull String type, ForgeMod[] modList) {
    /**
     * A forge mod for a server.
     *
     * @param name    The id of this mod.
     * @param version The version of this mod.
     */
    private record ForgeMod(@SerializedName("modid") @NonNull String name, String version) { }
}
