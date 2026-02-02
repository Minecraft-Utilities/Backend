package xyz.mcutils.backend.common.color;

import java.awt.Color;

/**
 * Result of parsing a hex color: the color and the number of characters consumed.
 */
public record HexColorResult(Color color, int charsConsumed) {}
