package xyz.mcutils.backend.discovery;

/**
 * Candidate generation strategies, ordered roughly by expected hit rate (highest first).
 */
public enum UsernameDiscoveryStrategy {
    SUFFIX_STATIC,
    SUFFIX_NUMERIC,
    SUFFIX_YEAR,
    PREFIX_STATIC,
    MINECRAFT_AFFIX,
    INCREMENT_SUFFIX,
    STRIPPED_BASE,
    WORD_PART,
    WORD_PAIR,
    WORD_TRIPLE,
    WORD_FULL_JOIN,
    UNDERSCORE_REMOVE,
    UNDERSCORE_INSERT,
    LEET_SPEAK,
    CHAR_OMIT,
    CHAR_DOUBLE;

    public String metricLabel() {
        return name().toLowerCase();
    }
}
