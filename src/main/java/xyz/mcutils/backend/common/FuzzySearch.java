package xyz.mcutils.backend.common;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Generic fuzzy string matching using Levenshtein distance.
 * Supports substring matching (score 0) and typo-tolerant fuzzy matching (score 1 to maxDistance).
 * Lower score = better match. Use {@link #matchScore} for single strings or {@link #search} for collections.
 */
@UtilityClass
public final class FuzzySearch {

    /**
     * Default maximum edit distance for fuzzy matches (typo tolerance).
     */
    public static final int DEFAULT_MAX_FUZZY_DISTANCE = 2;

    /**
     * Default pattern to split text into tokens for token-based fuzzy matching.
     */
    public static final Pattern DEFAULT_TOKEN_PATTERN = Pattern.compile("[^a-z0-9]+");

    /**
     * Levenshtein (edit) distance between two character sequences.
     *
     * @param a first sequence
     * @param b second sequence
     * @return number of insertions, deletions, or substitutions to transform a into b
     */
    public static int levenshteinDistance(CharSequence a, CharSequence b) {
        return levenshteinDistance(a, b, Integer.MAX_VALUE);
    }

    /**
     * Levenshtein distance with an early exit: computation stops as soon as the current row's
     * minimum exceeds {@code maxDistance} (the sequences cannot be within budget), and the
     * length difference is checked up front. Non-matches — the common case in registry search —
     * cost O(n × d) instead of O(n × m), and no per-call allocation beyond two small rows.
     *
     * @param a           first sequence
     * @param b           second sequence
     * @param maxDistance budget; the returned value is only meaningful when ≤ maxDistance
     * @return edit distance, or {@code maxDistance + 1} if it exceeds the budget
     */
    public static int levenshteinDistance(CharSequence a, CharSequence b, int maxDistance) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        if (Math.abs(n - m) > maxDistance) {
            return maxDistance + 1;
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                if (curr[j] < rowMin) {
                    rowMin = curr[j];
                }
            }
            if (rowMin > maxDistance) {
                return maxDistance + 1;
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
     * @param query            search query (trimmed and lowercased by this method)
     * @param text             text to match against (e.g. server name, hostname)
     * @param maxFuzzyDistance maximum Levenshtein distance to count as a match (e.g. 2 for typo tolerance)
     * @return 0 if query is a substring of text, 1..maxFuzzyDistance if within edit distance, -1 if no match
     */
    public static int matchScore(String query, String text, int maxFuzzyDistance) {
        return matchScore(query, text, maxFuzzyDistance, DEFAULT_TOKEN_PATTERN);
    }

    /**
     * Match score for query against a single text with custom token pattern.
     *
     * @param query            search query
     * @param text             text to match against
     * @param maxFuzzyDistance maximum edit distance for fuzzy match
     * @param tokenPattern     pattern to split text into tokens for token-based fuzzy matching (e.g. "wildnetwork" from "wildnetwork.net")
     * @return 0 = substring, 1..maxFuzzyDistance = fuzzy, -1 = no match
     */
    public static int matchScore(String query, String text, int maxFuzzyDistance, Pattern tokenPattern) {
        if (query == null || query.isBlank() || text == null) {
            return -1;
        }
        String normalizedQuery = query.trim().toLowerCase();
        if (normalizedQuery.isEmpty()) {
            return -1;
        }
        return matchScoreNormalized(normalizedQuery, text.toLowerCase(), maxFuzzyDistance, tokenPattern);
    }

    /**
     * Scoring with the query already trimmed/lowercased; used by {@link #bestMatchScore} so the
     * normalization cost is paid once per search rather than once per candidate text.
     */
    private static int matchScoreNormalized(String normalizedQuery, String lowerText, int maxFuzzyDistance, Pattern tokenPattern) {
        if (lowerText.contains(normalizedQuery)) {
            return 0;
        }
        int best = levenshteinDistance(normalizedQuery, lowerText, maxFuzzyDistance);
        if (best <= maxFuzzyDistance) {
            return best;
        }
        for (String token : tokenPattern.split(lowerText)) {
            if (token.length() < 2) {
                continue;
            }
            best = Math.min(best, levenshteinDistance(normalizedQuery, token, maxFuzzyDistance));
            if (best <= maxFuzzyDistance) {
                return best;
            }
        }
        return -1;
    }

    /**
     * Best match score for query against any of the given texts.
     *
     * @param query            search query
     * @param texts            candidate strings to match against (e.g. name, hostname, aliases)
     * @param maxFuzzyDistance maximum edit distance for fuzzy match
     * @return best score (0..maxFuzzyDistance) or -1 if no text matches
     */
    public static int bestMatchScore(String query, Collection<String> texts, int maxFuzzyDistance) {
        if (texts == null || texts.isEmpty()) {
            return -1;
        }
        String normalizedQuery = query == null ? null : query.trim().toLowerCase();
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return -1;
        }
        int best = -1;
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            int score = matchScoreNormalized(normalizedQuery, text.toLowerCase(), maxFuzzyDistance, DEFAULT_TOKEN_PATTERN);
            best = bestScore(best, score);
            if (best == 0) {
                break;
            }
        }
        return best;
    }

    /**
     * Search a list of items by fuzzy-matching the query against strings extracted from each item.
     * Results are ordered by best score (exact/substring first, then by fuzzy distance) and limited.
     * Uses a bounded top-K heap instead of sorting the entire match set.
     *
     * @param items            list to search
     * @param query            search query
     * @param textExtractor    function that returns the set of strings to match per item (e.g. name, hostname, aliases)
     * @param maxFuzzyDistance maximum edit distance for fuzzy match
     * @param limit            maximum number of results to return
     * @param <T>              item type
     * @return list of matching items, best matches first, size at most {@code limit}
     */
    public static <T> List<T> search(List<T> items, String query, Function<T, ? extends Collection<String>> textExtractor, int maxFuzzyDistance, int limit) {
        if (query == null || query.isBlank() || items == null || items.isEmpty() || limit <= 0) {
            return List.of();
        }
        // Worst-first heap bounded to `limit` keeps only the best matches; no full-list sort.
        // Tie-break by original index so equal scores keep stable list order (matches the old
        // full-sort behavior).
        PriorityQueue<Scored<T>> top = new PriorityQueue<>(Math.min(limit, 16),
                Comparator.comparingInt((Scored<T> scored) -> scored.score())
                        .reversed()
                        .thenComparing(Comparator.comparingInt((Scored<T> scored) -> scored.index()).reversed()));
        int index = 0;
        for (T item : items) {
            int score = bestMatchScore(query, textExtractor.apply(item), maxFuzzyDistance);
            if (score >= 0) {
                Scored<T> candidate = new Scored<>(item, score, index);
                if (top.size() < limit) {
                    top.add(candidate);
                } else if (candidate.score() < top.peek().score()) {
                    top.poll();
                    top.add(candidate);
                }
            }
            index++;
        }
        List<T> result = new ArrayList<>(top.size());
        while (!top.isEmpty()) {
            result.add(top.poll().item());
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Prefer lower (better) score; -1 means no match.
     */
    static int bestScore(int a, int b) {
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    /**
     * Result of scoring an item (item + match score + original list index for stable ordering).
     */
    public record Scored<T>(T item, int score, int index) {}
}
