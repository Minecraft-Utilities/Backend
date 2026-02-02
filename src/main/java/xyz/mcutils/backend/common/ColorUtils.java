package xyz.mcutils.backend.common;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.awt.*;
import java.util.Objects;
import java.util.regex.Pattern;

@UtilityClass
public final class ColorUtils {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)§[0-9A-FK-ORX]|§x(§[0-9A-F]){6}|§#[0-9A-Fa-f]{6}");

    /**
     * Strip the color codes
     * from the given input.
     *
     * @param input the input to strip
     * @return the stripped input
     */
    @NonNull
    public static String stripColor(@NonNull String input) {
        return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * Convert the given input into HTML format.
     * <p>
     * This will replace each color code with
     * a span tag with the respective color in
     * hex format. Supports legacy color codes,
     * hex colors (gradients), and formatting codes.
     * </p>
     *
     * @param input the input to convert
     * @return the converted input
     */
    @NonNull
    public static String toHTML(@NonNull String input) {
        StringBuilder result = new StringBuilder();
        StringBuilder pending = new StringBuilder();
        String color = null, activeColor = null;
        int fmt = 0, activeFmt = 0; // Bitmask: 1=bold, 2=italic, 4=underline, 8=strikethrough
        boolean hasSpan = false;

        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '§' && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);

                // Hex color: §x§R§R§G§G§B§B
                if (code == 'x' && i + 13 < chars.length) {
                    StringBuilder hex = new StringBuilder("#");
                    boolean valid = true;
                    for (int j = 0; j < 6 && valid; j++) {
                        int idx = i + 2 + (j * 2);
                        if (idx + 1 < chars.length && chars[idx] == '§') hex.append(chars[idx + 1]);
                        else valid = false;
                    }
                    if (valid) { color = hex.toString(); i += 13; continue; }
                }
                if (code == 'r') { color = null; fmt = 0; i++; continue; }
                MinecraftColor mcColor = MinecraftColor.getByCode(code);
                if (mcColor != null) { color = mcColor.toHex(); i++; continue; }
                if (code == 'l') { fmt |= 1; i++; continue; }
                if (code == 'o') { fmt |= 2; i++; continue; }
                if (code == 'n') { fmt |= 4; i++; continue; }
                if (code == 'm') { fmt |= 8; i++; continue; }
                if (code == 'k') { i++; continue; }
            }

            // Check if style changed
            boolean sameStyle = Objects.equals(color, activeColor) && fmt == activeFmt;
            if (!sameStyle) {
                if (!pending.isEmpty()) {
                    if (hasSpan) result.append(buildSpan(activeColor, activeFmt, pending.toString()));
                    else result.append(pending);
                    pending.setLength(0);
                }
                activeColor = color;
                activeFmt = fmt;
                hasSpan = activeColor != null || activeFmt != 0;
            }

            // Escape and buffer
            pending.append(switch (chars[i]) {
                case ' ' -> "&nbsp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '&' -> "&amp;";
                case '"' -> "&quot;";
                default -> chars[i];
            });
        }

        // Flush remaining
        if (!pending.isEmpty()) {
            if (hasSpan) result.append(buildSpan(activeColor, activeFmt, pending.toString()));
            else result.append(pending);
        }
        return result.toString();
    }

    private static String buildSpan(String color, int fmt, String content) {
        StringBuilder style = new StringBuilder();
        if (color != null) style.append("color:").append(color);
        if ((fmt & 1) != 0) style.append(style.isEmpty() ? "" : ";").append("font-weight:bold");
        if ((fmt & 2) != 0) style.append(style.isEmpty() ? "" : ";").append("font-style:italic");
        if ((fmt & 12) != 0) {
            style.append(style.isEmpty() ? "" : ";").append("text-decoration:");
            style.append((fmt & 4) != 0 && (fmt & 8) != 0 ? "underline line-through" : (fmt & 4) != 0 ? "underline" : "line-through");
        }
        return "<span style=\"" + style + "\">" + content + "</span>";
    }

    /**
     * Parses a hex color code in the format §x§R§R§G§G§B§B from the given string at the specified index.
     * <p>
     * This method checks if the substring starting at the given index matches the hex color format
     * (§x followed by 6 pairs of § + hex digit). If valid, it returns the parsed Color and the
     * number of characters consumed (14). If invalid or the string is too short, returns null.
     * </p>
     *
     * @param line  the string containing the color code
     * @param index the index of the leading § character
     * @return the parsed Color if valid, null otherwise
     */
    public static Color parseHexColor(@NonNull String line, int index) {
        // Check if we have enough characters for §x§R§R§G§G§B§B (14 characters total)
        if (index + 14 > line.length()) {
            return null;
        }

        // Check if the character at index is § and the next is 'x' (case-insensitive)
        if (line.charAt(index) != '§' || Character.toLowerCase(line.charAt(index + 1)) != 'x') {
            return null;
        }

        // Build the hex string by extracting the 6 hex digits
        StringBuilder hex = new StringBuilder("#");
        for (int j = 0; j < 6; j++) {
            int idx = index + 2 + (j * 2);
            // Check if we have enough characters and if it's a § followed by a hex digit
            if (idx + 1 >= line.length() || line.charAt(idx) != '§') {
                return null;
            }
            char hexDigit = line.charAt(idx + 1);
            // Validate hex digit (0-9, A-F, a-f)
            if ((hexDigit >= '0' && hexDigit <= '9') || 
                (hexDigit >= 'A' && hexDigit <= 'F') || 
                (hexDigit >= 'a' && hexDigit <= 'f')) {
                hex.append(hexDigit);
            } else {
                return null;
            }
        }

        // Parse and return the color
        try {
            return Color.decode(hex.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a §#RRGGBB hex color at the given index. Returns the Color if valid, null otherwise.
     * Supports gradient MOTDs where each character can have its own color.
     */
    public static Color parseSharpHexColor(@NonNull String line, int index) {
        if (index + 8 > line.length() || line.charAt(index) != '§' || line.charAt(index + 1) != '#') {
            return null;
        }
        String hex = line.substring(index + 2, index + 8);
        if (!hex.matches("[0-9A-Fa-f]{6}")) {
            return null;
        }
        try {
            return Color.decode("#" + hex);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}