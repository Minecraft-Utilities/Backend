package xyz.mcutils.backend.common;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * @author Braydon
 */
@UtilityClass
public final class EnumUtils {
    /**
     * Get the enum constant of the specified enum type with the specified name.
     * This method performs a case-insensitive match.
     *
     * @param enumType the enum type
     * @param name     the name of the constant to return (case-insensitive)
     * @param <T>      the type of the enum
     * @return the enum constant of the specified enum type with the specified name, or null if not found
     */
    public <T extends Enum<T>> T getEnumConstant(@NonNull Class<T> enumType, @NonNull String name) {
        for (T constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        return null;
    }
}