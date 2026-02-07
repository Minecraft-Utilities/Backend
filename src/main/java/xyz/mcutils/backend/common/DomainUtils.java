package xyz.mcutils.backend.common;

/**
 * Utilities for domain/hostname matching.
 */
public final class DomainUtils {

    private DomainUtils() { }

    /**
     * Returns whether a hostname matches a wildcard pattern (e.g. {@code *.example.com}).
     * The hostname should already be normalized (e.g. lowercased).
     *
     * @param pattern            the pattern, optionally starting with {@code *.}
     * @param normalizedHostname the hostname to match, in normalized form
     * @return true if the hostname matches the pattern
     */
    public static boolean matchesWildcard(String pattern, String normalizedHostname) {
        if (pattern == null) return false;
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1).toLowerCase();
            String domainOnly = pattern.substring(2).toLowerCase();
            return normalizedHostname.equals(domainOnly) || normalizedHostname.endsWith(suffix);
        }
        return pattern.equalsIgnoreCase(normalizedHostname);
    }
}
