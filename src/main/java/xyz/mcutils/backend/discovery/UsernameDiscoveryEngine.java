package xyz.mcutils.backend.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates username discovery candidates from a known player using multiple strategies.
 */
public final class UsernameDiscoveryEngine {
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;
    private static final Pattern VALID = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern TRAILING_NUMERIC_SUFFIX = Pattern.compile("[_]?\\d+$");
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Pattern TRAILING_UNDERSCORES = Pattern.compile("_+$");
    private static final Pattern LEADING_UNDERSCORES = Pattern.compile("^_+");
    private static final Pattern TRAILING_DECORATION = Pattern.compile("(?:_+\\d+|_+)+$");

    private final UsernameDiscoveryConfig config;

    public UsernameDiscoveryEngine(UsernameDiscoveryConfig config) {
        this.config = config;
    }

    public List<UsernameCandidate> generateCandidates(String sourceUsername) {
        if (sourceUsername == null || sourceUsername.isBlank()) {
            return List.of();
        }

        String original = sourceUsername.strip();
        String originalLower = original.toLowerCase(Locale.ROOT);
        Map<String, UsernameDiscoveryStrategy> byUsername = new LinkedHashMap<>();
        List<String> bases = deriveBases(original);
        List<String> words = splitWords(original);

        for (UsernameDiscoveryStrategy strategy : config.enabledStrategies()) {
            if (byUsername.size() >= config.maxCandidatesPerPlayer()) {
                break;
            }
            int remaining = config.maxCandidatesPerPlayer() - byUsername.size();
            Set<String> produced = new LinkedHashSet<>();
            switch (strategy) {
                case SUFFIX_STATIC -> applyStaticSuffixes(produced, bases, remaining);
                case SUFFIX_NUMERIC -> applyNumericSuffixes(produced, bases, remaining);
                case SUFFIX_YEAR -> applyYearSuffixes(produced, bases, remaining);
                case PREFIX_STATIC -> applyStaticPrefixes(produced, bases, remaining);
                case MINECRAFT_AFFIX -> applyMinecraftAffixes(produced, bases, remaining);
                case INCREMENT_SUFFIX -> applyIncrementSuffix(produced, original, remaining);
                case STRIPPED_BASE -> applyStrippedBases(produced, original, remaining);
                case WORD_PART -> applyWordParts(produced, words, remaining);
                case WORD_PAIR -> applyWordPairs(produced, words, remaining);
                case WORD_TRIPLE -> applyWordTriples(produced, words, remaining);
                case WORD_FULL_JOIN -> applyWordFullJoin(produced, words, remaining);
                case UNDERSCORE_REMOVE -> applyUnderscoreRemove(produced, original, remaining);
                case UNDERSCORE_INSERT -> applyUnderscoreInsert(produced, original, remaining);
                case LEET_SPEAK -> applyLeetSpeak(produced, bases, remaining);
                case CHAR_OMIT -> applyCharOmit(produced, bases, remaining);
                case CHAR_DOUBLE -> applyCharDouble(produced, bases, remaining);
            }

            for (String candidate : produced) {
                String lower = candidate.toLowerCase(Locale.ROOT);
                if (lower.equals(originalLower)) {
                    continue;
                }
                byUsername.putIfAbsent(lower, strategy);
                if (byUsername.size() >= config.maxCandidatesPerPlayer()) {
                    return toCandidateList(byUsername);
                }
            }
        }

        return toCandidateList(byUsername);
    }

    public static boolean isValidCandidate(String username) {
        if (username == null) {
            return false;
        }
        int length = username.length();
        return length >= MIN_LENGTH && length <= MAX_LENGTH && VALID.matcher(username).matches();
    }

    public static String stripTrailingNumericSuffix(String username) {
        if (username == null) {
            return "";
        }
        return TRAILING_NUMERIC_SUFFIX.matcher(username).replaceAll("");
    }

    private List<UsernameCandidate> toCandidateList(Map<String, UsernameDiscoveryStrategy> byUsername) {
        List<UsernameCandidate> candidates = new ArrayList<>(byUsername.size());
        for (Map.Entry<String, UsernameDiscoveryStrategy> entry : byUsername.entrySet()) {
            candidates.add(new UsernameCandidate(entry.getKey(), entry.getValue()));
        }
        return candidates;
    }

    private List<String> deriveBases(String username) {
        LinkedHashSet<String> bases = new LinkedHashSet<>();
        bases.add(username);
        String noTrailingNumbers = stripTrailingNumericSuffix(username);
        if (!noTrailingNumbers.isBlank()) {
            bases.add(noTrailingNumbers);
        }
        String noDecoration = TRAILING_DECORATION.matcher(username).replaceAll("");
        if (!noDecoration.isBlank()) {
            bases.add(noDecoration);
        }
        String trimmedUnderscores = TRAILING_UNDERSCORES.matcher(LEADING_UNDERSCORES.matcher(username).replaceAll("")).replaceAll("");
        if (!trimmedUnderscores.isBlank()) {
            bases.add(trimmedUnderscores);
        }
        return List.copyOf(bases);
    }

    private void applyStaticSuffixes(Set<String> out, List<String> bases, int maxToAdd) {
        outer:
        for (String base : bases) {
            for (String suffix : config.staticSuffixes()) {
                if (!addIfValid(out, base + suffix, maxToAdd)) {
                    break outer;
                }
            }
        }
    }

    private void applyNumericSuffixes(Set<String> out, List<String> bases, int maxToAdd) {
        outer:
        for (String base : bases) {
            int max = base.length() <= 6 ? config.numericSuffixExtendedMax() : config.numericSuffixMax();
            for (int i = 0; i <= max; i++) {
                if (!addIfValid(out, base + i, maxToAdd)) {
                    break outer;
                }
                if (base.length() <= 10 && !addIfValid(out, base + "_" + i, maxToAdd)) {
                    break outer;
                }
            }
        }
    }

    private void applyYearSuffixes(Set<String> out, List<String> bases, int maxToAdd) {
        outer:
        for (String base : bases) {
            if (base.length() > 11) {
                continue;
            }
            for (Integer year : config.birthYears()) {
                if (!addIfValid(out, base + year, maxToAdd)) {
                    break outer;
                }
                if (!addIfValid(out, base + "_" + year, maxToAdd)) {
                    break outer;
                }
            }
        }
    }

    private void applyStaticPrefixes(Set<String> out, List<String> bases, int maxToAdd) {
        outer:
        for (String base : bases) {
            for (String prefix : config.staticPrefixes()) {
                if (!addIfValid(out, prefix + base, maxToAdd)) {
                    break outer;
                }
                if (!addIfValid(out, prefix + "_" + base, maxToAdd)) {
                    break outer;
                }
            }
        }
    }

    private void applyMinecraftAffixes(Set<String> out, List<String> bases, int maxToAdd) {
        outer:
        for (String base : bases) {
            for (String affix : config.minecraftAffixes()) {
                if (affix.endsWith("_")) {
                    if (!addIfValid(out, affix + base, maxToAdd)) {
                        break outer;
                    }
                } else if (affix.startsWith("_")) {
                    if (!addIfValid(out, base + affix, maxToAdd)) {
                        break outer;
                    }
                } else {
                    if (!addIfValid(out, base + affix, maxToAdd)
                            || !addIfValid(out, base + "_" + affix, maxToAdd)
                            || !addIfValid(out, affix + base, maxToAdd)
                            || !addIfValid(out, affix + "_" + base, maxToAdd)) {
                        break outer;
                    }
                }
            }
        }
    }

    private void applyIncrementSuffix(Set<String> out, String username, int maxToAdd) {
        Matcher matcher = Pattern.compile("^(.*?)([_]?)(\\d+)$").matcher(username);
        if (!matcher.matches()) {
            return;
        }
        String base = matcher.group(1);
        String separator = matcher.group(2);
        int value = Integer.parseInt(matcher.group(3));
        for (int delta : new int[]{1, -1, 2, -2, 10, -10}) {
            int next = value + delta;
            if (next < 0) {
                continue;
            }
            if (!addIfValid(out, base + separator + next, maxToAdd)) {
                return;
            }
        }
    }

    private void applyStrippedBases(Set<String> out, String username, int maxToAdd) {
        for (String base : deriveBases(username)) {
            if (!addIfValid(out, base, maxToAdd)) {
                return;
            }
            if (base.contains("_") && !addIfValid(out, base.replace("_", ""), maxToAdd)) {
                return;
            }
        }
    }

    private void applyWordParts(Set<String> out, List<String> words, int maxToAdd) {
        for (String word : words) {
            if (!addIfValid(out, word, maxToAdd)) {
                return;
            }
        }
    }

    private void applyWordPairs(Set<String> out, List<String> words, int maxToAdd) {
        outer:
        for (int i = 0; i < words.size(); i++) {
            for (int j = 0; j < words.size(); j++) {
                if (i == j) {
                    continue;
                }
                if (!addIfValid(out, words.get(i) + "_" + words.get(j), maxToAdd)) {
                    break outer;
                }
            }
        }
    }

    private void applyWordTriples(Set<String> out, List<String> words, int maxToAdd) {
        if (words.size() < 3) {
            return;
        }
        outer:
        for (int i = 0; i < words.size(); i++) {
            for (int j = 0; j < words.size(); j++) {
                if (j == i) {
                    continue;
                }
                for (int k = 0; k < words.size(); k++) {
                    if (k == i || k == j) {
                        continue;
                    }
                    if (!addIfValid(out, words.get(i) + "_" + words.get(j) + "_" + words.get(k), maxToAdd)) {
                        return;
                    }
                }
            }
        }
    }

    private void applyWordFullJoin(Set<String> out, List<String> words, int maxToAdd) {
        if (words.size() < 2) {
            return;
        }
        if (!addIfValid(out, String.join("_", words), maxToAdd)) {
            return;
        }
        addIfValid(out, String.join("", words), maxToAdd);
    }

    private void applyUnderscoreRemove(Set<String> out, String username, int maxToAdd) {
        if (!username.contains("_")) {
            return;
        }
        if (!addIfValid(out, username.replace("_", ""), maxToAdd)) {
            return;
        }
        for (String part : username.split("_")) {
            if (!part.isBlank() && !addIfValid(out, part, maxToAdd)) {
                return;
            }
        }
    }

    private void applyUnderscoreInsert(Set<String> out, String username, int maxToAdd) {
        String compact = username.replace("_", "");
        if (!compact.equals(username) || compact.length() < 5 || compact.length() > 12) {
            return;
        }
        for (int i = 1; i < compact.length(); i++) {
            if (!addIfValid(out, compact.substring(0, i) + "_" + compact.substring(i), maxToAdd)) {
                return;
            }
        }
    }

    private void applyLeetSpeak(Set<String> out, List<String> bases, int maxToAdd) {
        outer:
        for (String base : bases) {
            if (base.length() > 10) {
                continue;
            }
            int variants = 0;
            for (int mask = 1; mask < (1 << 5) && variants < config.maxLeetVariantsPerBase(); mask++) {
                String transformed = base;
                if ((mask & 1) != 0) {
                    transformed = replaceIgnoreCase(transformed, 'a', '4');
                }
                if ((mask & 2) != 0) {
                    transformed = replaceIgnoreCase(transformed, 'e', '3');
                }
                if ((mask & 4) != 0) {
                    transformed = replaceIgnoreCase(transformed, 'i', '1');
                }
                if ((mask & 8) != 0) {
                    transformed = replaceIgnoreCase(transformed, 'o', '0');
                }
                if ((mask & 16) != 0) {
                    transformed = replaceIgnoreCase(transformed, 's', '5');
                }
                if (!transformed.equals(base) && isValidCandidate(transformed)) {
                    if (!out.add(transformed)) {
                        continue;
                    }
                    variants++;
                    if (out.size() >= maxToAdd) {
                        break outer;
                    }
                }
            }
        }
    }

    private void applyCharOmit(Set<String> out, List<String> bases, int maxToAdd) {
        outer:
        for (String base : bases) {
            if (base.length() < 4 || base.length() > 12) {
                continue;
            }
            int variants = 0;
            for (int i = 0; i < base.length() && variants < config.maxOmitVariantsPerBase(); i++) {
                if (base.charAt(i) == '_') {
                    continue;
                }
                String candidate = base.substring(0, i) + base.substring(i + 1);
                if (addIfValid(out, candidate, maxToAdd)) {
                    variants++;
                } else {
                    break outer;
                }
            }
        }
    }

    private void applyCharDouble(Set<String> out, List<String> bases, int maxToAdd) {
        for (String base : bases) {
            if (base.isEmpty()) {
                continue;
            }
            if (!addIfValid(out, base + base.charAt(base.length() - 1), maxToAdd)) {
                return;
            }
            if (base.length() >= 2 && !addIfValid(out, base.charAt(0) + base, maxToAdd)) {
                return;
            }
            int underscore = base.indexOf('_');
            if (underscore > 0 && underscore < base.length() - 1) {
                addIfValid(out, base.substring(0, underscore + 1) + base.charAt(underscore + 1) + base.substring(underscore + 1), maxToAdd);
            }
        }
    }

    private static boolean addIfValid(Set<String> out, String username, int maxToAdd) {
        if (!isValidCandidate(username)) {
            return true;
        }
        out.add(username);
        return out.size() < maxToAdd;
    }

    private static String replaceIgnoreCase(String input, char from, char to) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (Character.toLowerCase(ch) == from) {
                builder.append(to);
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static List<String> splitWords(String username) {
        List<String> parts = new ArrayList<>();
        for (String segment : username.split("_")) {
            if (segment.isBlank()) {
                continue;
            }
            for (String word : CAMEL_CASE_BOUNDARY.split(segment)) {
                String normalized = word.toLowerCase(Locale.ROOT);
                if (!normalized.isBlank() && normalized.length() >= MIN_LENGTH) {
                    parts.add(normalized);
                }
            }
        }
        return parts.stream().distinct().toList();
    }
}
