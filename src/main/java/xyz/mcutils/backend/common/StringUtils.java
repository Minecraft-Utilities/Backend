package xyz.mcutils.backend.common;

/**
 * Utilities for string handling.
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Caps a value at {@code maxLength} characters (code points), matching how PostgreSQL
     * counts {@code VARCHAR(n)}. Truncating by UTF-16 units instead could split a surrogate
     * pair and, more importantly, disagree with the server's character count on astral
     * characters — the database accepts a value with at most {@code maxLength} code points,
     * so cutting on a code-point boundary is the only safe cut.
     *
     * @param value     the value to truncate; may be {@code null}
     * @param maxLength the maximum length in characters (code points)
     * @return the truncated value, or {@code null} when {@code value} is {@code null}
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }
}