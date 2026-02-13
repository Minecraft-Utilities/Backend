package xyz.mcutils.backend.common;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import xyz.mcutils.backend.exception.impl.BadRequestException;

import java.util.UUID;

@UtilityClass
@Slf4j
public class PlayerUtils {

    /**
     * Gets the UUID from the string.
     *
     * @param id the id string
     * @return the UUID
     */
    public static UUID getUuidFromString(String id) {
        UUID uuid;
        boolean isFullUuid = id.length() == 36;
        if (id.length() == 32 || isFullUuid) {
            try {
                uuid = isFullUuid ? UUID.fromString(id) : UUIDUtils.addDashes(id);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Invalid UUID provided: '%s'".formatted(id));
            }
            return uuid;
        }
        return null;
    }
}
