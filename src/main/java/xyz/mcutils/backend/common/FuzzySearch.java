package xyz.mcutils.backend.common;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Generic fuzzy string matching using Levenshtein distance.
 * Supports substring matching (score 0) and typo-tolerant fuzzy matching (score 1 to maxDistance).
 * Lower score = better match. Use {@link #matchScore} for single strings or {@link #search} for collections.
 */
@UtilityClass
public final class FuzzySearch {

    /** Default maximum edit distance for fuzzy matches (typo tolerance). */
    public static final int DEFAULT_MAX_FUZZY_DISTANCE = 2;

    /** Default pattern to split text into tokens for token-based fuzzy matching. */
    public static final Pattern DEFAULT_TOKEN_PATTERN = Pattern.compile("[^a-z0-9]+");

    /**
     * Levenshtein (edit) distance between two character sequences.
     *
     * @param a first sequence
     * @param b second sequence
     * @return number of insertions, deletions, or substitutions to transform a into b
     */
    public static int levenshteinDistance(CharSequence a, CharSequence b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = curr;
            curr = t;
        }
        return prev[m];
    }

    /**
     * Match score for query against a single text: 0 = substring match, 1..maxFuzzyDistance = fuzzy match, -1 = no match.
     * Comparison is case-insensitive. Checks substring first, then fuzzy match on the full text and on tokens.
     *
     * @param query             search query (trimmed and lowercased by this method)
     * @param text              text to match against (e.g. server name, hostname)
     * @param maxFuzzyDistance  maximum Levenshtein distance to count as a match (e.g. 2 for typo tolerance)
     * @return 0 if query is a substring of text, 1..maxFuzzyDistance if within edit distance, -1 if no match
     */
    public static int matchScore(String query, String text, int maxFuzzyDistance) {
        return matchScore(query, text, maxFuzzyDistance, DEFAULT_TOKEN_PATTERN);
    }

    /**
     * Match score for query against a single text with custom token pattern.
     *
     * @param query             search query
     * @param text              text to match against
     * @param maxFuzzyDistance  maximum edit distance for fuzzy match
     * @param tokenPattern      pattern to split text into tokens for token-based fuzzy matching (e.g. "wildnetwork" from "wildnetwork.net")
     * @return 0 = substring, 1..maxFuzzyDistance = fuzzy, -1 = no match
     */
    public static int matchScore(String query, String text, int maxFuzzyDistance, Pattern tokenPattern) {
        if (query == null || query.isBlank() || text == null) {
            return -1;
        }
        String normalizedQuery = query.trim().toLowerCase();
        String lower = text.toLowerCase();
        if (normalizedQuery.isEmpty()) {
            return -1;
        }
        if (lower.contains(normalizedQuery)) {
            return 0;
        }
        int best = levenshteinDistance(normalizedQuery, lower);
        if (best <= maxFuzzyDistance) {
            return best;
        }
        for (String token : tokenPattern.split(lower)) {
            if (token.length() < 2) continue;
            best = Math.min(best, levenshteinDistance(normalizedQuery, token));
            if (best <= maxFuzzyDistance) {
                return best;
            }
        }
        return best <= maxFuzzyDistance ? best : -1;
    }

    /**
     * Best match score for query against any of the given texts.
     *
     * @param query             search query
     * @param texts             candidate strings to match against (e.g. name, hostname, aliases)
     * @param maxFuzzyDistance  maximum edit distance for fuzzy match
     * @return best score (0..maxFuzzyDistance) or -1 if no text matches
     */
    public static int bestMatchScore(String query, Collection<String> texts, int maxFuzzyDistance) {
        if (texts == null || texts.isEmpty()) {
            return -1;
        }
        int best = -1;
        for (String text : texts) {
            if (text == null) continue;
            int score = matchScore(query, text, maxFuzzyDistance);
            best = bestScore(best, score);
            if (best == 0) break;
        }
        return best;
    }

    /**
     * Search a list of items by fuzzy-matching the query against strings extracted from each item.
     * Results are ordered by best score (exact/substring first, then by fuzzy distance) and limited.
     *
     * @param items         list to search
     * @param query         search query
     * @param textExtractor function that returns the set of strings to match per item (e.g. name, hostname, aliases)
     * @param maxFuzzyDistance maximum edit distance for fuzzy match
     * @param limit         maximum number of results to return
     * @param <T>           item type
     * @return list of matching items, best matches first, size at most {@code limit}
     */
    public static <T> List<T> search(
            List<T> items,
            String query,
            Function<T, ? extends Collection<String>> textExtractor,
            int maxFuzzyDistance,
            int limit) {
        if (query == null || query.isBlank() || items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new Scored<>(item, bestMatchScore(query, textExtractor.apply(item), maxFuzzyDistance)))
                .filter(scored -> scored.score() >= 0)
                .sorted((a, b) -> Integer.compare(a.score(), b.score()))
                .limit(limit)
                .map(Scored::item)
                .toList();
    }

    /**
     * Prefer lower (better) score; -1 means no match.
     */
    static int bestScore(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /** Result of scoring an item (item + match score). */
    public record Scored<T>(T item, int score) {}
}
