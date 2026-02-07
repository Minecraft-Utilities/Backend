package xyz.mcutils.backend.model.domain.server.java;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import xyz.mcutils.backend.common.JavaMinecraftVersion;

@AllArgsConstructor
@Getter
public class JavaVersion {
    /**
     * The version name of the server.
     */
    @NonNull
    private final String name;

    /**
     * The server platform.
     */
    private String platform;

    /**
     * The protocol version.
     */
    private final int protocol;

    /**
     * The name of the protocol, null if not found.
     */
    private final String protocolName;

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
                platform = split[0];
            }
        }
        JavaMinecraftVersion minecraftVersion = JavaMinecraftVersion.byProtocol(protocol);
        return new JavaVersion(name, platform, protocol, minecraftVersion == null ? null : minecraftVersion.getName());
    }
}
