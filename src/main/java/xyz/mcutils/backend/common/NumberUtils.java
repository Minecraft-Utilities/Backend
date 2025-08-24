package xyz.mcutils.backend.common;

public class NumberUtils {
    private final static String NUMBER_PATTERN = "#,###.##";
    private final static java.text.DecimalFormat FORMATTER = new java.text.DecimalFormat(NUMBER_PATTERN);

    /**
     * Formats a number with a comma separator and two decimal places.
     *
     * @param value the number to format.
     * @return the formatted number.
     */
    public static String formatNumber(double value) {
        return FORMATTER.format(value);
    }
}
