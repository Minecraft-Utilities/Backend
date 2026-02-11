package xyz.mcutils.backend.common;


import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class UUIDUtils {

    /**
     * Add dashes to a UUID.
     *
     * @param trimmed the UUID without dashes
     * @return the UUID with dashes
     */
    @NonNull
    public static UUID addDashes(@NonNull String trimmed) {
        StringBuilder builder = new StringBuilder(trimmed);
        for (int i = 0, pos = 20; i < 4; i++, pos -= 4) {
            builder.insert(pos, "-");
        }
        return UUID.fromString(builder.toString());
    }

    /**
     * Parses a string as a UUID (32 hex chars or 36 with dashes). Returns null if invalid.
     */
    public static UUID parseUuid(String s) {
        if (s == null || (s.length() != 32 && s.length() != 36)) return null;
        try {
            return s.length() == 36 ? UUID.fromString(s) : addDashes(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
