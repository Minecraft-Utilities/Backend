package xyz.mcutils.backend.model.domain.server.java;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import xyz.mcutils.backend.common.JavaMinecraftVersion;
import xyz.mcutils.backend.common.color.ColorUtils;

@AllArgsConstructor
@Getter
public class JavaVersion {
    /**
     * The version name of the server.
     */
    @NonNull
    private final String name;
    /**
     * The protocol version.
     */
    private final int protocol;
    /**
     * The name of the protocol, null if not found.
     */
    private final String protocolName;
    /**
     * The server platform.
     */
    private String platform;

    /**
     * Create a more detailed
     * copy of this object.
     *
     * @return the detailed copy
     */
    @NonNull
    public JavaVersion detailedCopy() {
        String platform = null;
        if (name.contains(" ")) { // Parse the server platform
            String[] split = name.split(" ");
            if (split.length == 2) {
                platform = ColorUtils.stripColor(split[0]); // Strip legacy/modern color codes ("§4Paper" -> "Paper")
                if (platform.isBlank()) {
                    platform = null;
                }
            }
        }
        JavaMinecraftVersion minecraftVersion = JavaMinecraftVersion.byProtocol(protocol);
        return new JavaVersion(name, protocol, minecraftVersion == null ? null : minecraftVersion.getName(), platform);
    }
}
