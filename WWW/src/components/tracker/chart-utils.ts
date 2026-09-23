/**
 * Colors for chart segments, tuned for the site's dark theme.
 * Kept in oklch to match the design tokens in `globals.css`.
 */
export const CHART_COLORS = [
  "oklch(0.72 0.22 285)",
  "oklch(0.72 0.2 320)",
  "oklch(0.72 0.16 250)",
  "oklch(0.76 0.14 205)",
  "oklch(0.76 0.16 165)",
  "oklch(0.8 0.15 85)",
  "oklch(0.72 0.19 350)",
  "oklch(0.8 0.18 120)",
  "oklch(0.7 0.19 25)",
  "oklch(0.66 0.24 305)",
];

/** Color used for the `unknown` bucket. */
export const UNKNOWN_COLOR = "oklch(0.55 0.01 280)";

/**
 * Returns the chart color for the given bucket, gray for unknown values.
 *
 * @param key the bucket key (eg: platform or country name)
 * @param index the position of the bucket in the sorted list
 */
export function colorFor(key: string, index: number): string {
  return key.toLowerCase() === "unknown" ? UNKNOWN_COLOR : CHART_COLORS[index % CHART_COLORS.length];
}

/**
 * Formats a share of a total as a percentage: one decimal below 10%, integers above.
 *
 * @param part the part count
 * @param total the total count
 */
export function formatPercent(part: number, total: number): string {
  if (total <= 0) return "0%";
  const value = (part / total) * 100;
  return `${value >= 10 ? Math.round(value) : Math.round(value * 10) / 10}%`;
}

/**
 * Known Java Edition release protocol numbers -> Minecraft version label.
 * Source: https://minecraft.wiki/w/Protocol_version (accessed 2026-09-20)
 */
const PROTOCOL_LABELS: Record<string, string> = {
  "47": "1.8",
  "107": "1.9",
  "108": "1.9.1",
  "109": "1.9.2",
  "110": "1.9.3/1.9.4",
  "210": "1.10",
  "315": "1.11",
  "316": "1.11.1/1.11.2",
  "335": "1.12",
  "338": "1.12.1",
  "340": "1.12.2",
  "393": "1.13",
  "401": "1.13.1",
  "404": "1.13.2",
  "477": "1.14",
  "480": "1.14.1",
  "485": "1.14.2",
  "490": "1.14.3",
  "498": "1.14.4",
  "573": "1.15",
  "575": "1.15.1",
  "578": "1.15.2",
  "735": "1.16",
  "736": "1.16.1",
  "751": "1.16.2",
  "753": "1.16.3",
  "754": "1.16.4/1.16.5",
  "755": "1.17",
  "756": "1.17.1",
  "757": "1.18/1.18.1",
  "758": "1.18.2",
  "759": "1.19",
  "760": "1.19.1/1.19.2",
  "761": "1.19.3",
  "762": "1.19.4",
  "763": "1.20.1-1.20.4",
  "764": "1.20.3 Pre-Release",
  "765": "1.20.4 Pre-Release",
  "766": "1.20.5/1.20.6",
  "767": "1.21/1.21.1",
  "768": "1.21.2/1.21.3",
  "769": "1.21.4",
  "770": "1.21.5",
  "771": "1.21.6",
  "772": "1.21.7/1.21.8",
  "773": "1.21.9/1.21.10",
  "774": "1.21.11",
  "775": "26.1-26.1.2",
  "776": "26.2",
  "777": "26.3",
};

/**
 * Human-readable label for a status protocol number, eg: `763` -> "1.20.1-1.20.4".
 *
 * @param protocol the protocol number as reported by the API
 */
export function protocolLabel(protocol: string): string {
  return PROTOCOL_LABELS[protocol] ?? protocol;
}

const regionNames = new Intl.DisplayNames(["en"], { type: "region" });

/**
 * Human-readable country name for an ISO country code, eg: `US` -> "United States".
 *
 * @param code the country code as reported by the API
 */
export function countryLabel(code: string): string {
  const normalized = code.toUpperCase();
  try {
    return regionNames.of(normalized) ?? normalized;
  } catch {
    return normalized;
  }
}

/**
 * Flag emoji for a two-letter ISO country code, or `null` when the code is not a plain country.
 *
 * @param code the country code as reported by the API
 */
export function countryFlag(code: string): string | null {
  const normalized = code.toUpperCase();
  if (!/^[A-Z]{2}$/.test(normalized)) return null;
  return String.fromCodePoint(...[...normalized].map(char => 0x1f1e6 + char.charCodeAt(0) - 65));
}
