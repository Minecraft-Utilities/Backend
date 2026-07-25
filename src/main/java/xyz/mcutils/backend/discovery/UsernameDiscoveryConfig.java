package xyz.mcutils.backend.discovery;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tunable limits and word lists for username discovery generation.
 */
public record UsernameDiscoveryConfig(
        int maxCandidatesPerPlayer,
        int numericSuffixMax,
        int numericSuffixExtendedMax,
        int maxLeetVariantsPerBase,
        int maxOmitVariantsPerBase,
        List<String> staticSuffixes,
        List<String> staticPrefixes,
        List<String> minecraftAffixes,
        List<Integer> birthYears
) {
    public static final List<String> DEFAULT_SUFFIXES = List.of(
            "_", "__", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "01", "02", "03", "10", "11", "12", "13", "21", "22", "23",
            "69", "99", "123", "124", "321", "420", "777", "1337"
    );

    public static final List<String> DEFAULT_PREFIXES = List.of(
            "xX", "Xx", "xx", "Mr", "Mrs", "The", "Its", "Im", "Dr", "Sir",
            "Lord", "OG", "VIP", "Big", "Lil", "Real", "Not", "Just"
    );

    public static final List<String> DEFAULT_AFFIXES = List.of(
            "_mc", "_yt", "_tv", "_pvp", "_gg", "_pro", "_hd", "_op",
            "mc_", "yt_", "tv_", "Mr", "Gamer", "Gaming", "Pro", "HD",
            "Playz", "Plays", "Live", "LIVE", "Fan", "Fanboy"
    );

    public static final List<Integer> DEFAULT_BIRTH_YEARS = List.of(
            1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007,
            2008, 2009, 2010, 2011, 2012, 2013, 2014, 2015
    );

    public UsernameDiscoveryConfig {
        staticSuffixes = List.copyOf(staticSuffixes);
        staticPrefixes = List.copyOf(staticPrefixes);
        minecraftAffixes = List.copyOf(minecraftAffixes);
        birthYears = List.copyOf(birthYears);
    }

    public Set<UsernameDiscoveryStrategy> enabledStrategies() {
        return Set.of(UsernameDiscoveryStrategy.values());
    }
}
