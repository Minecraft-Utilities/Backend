import {
  serializeTrackerServerQuery,
  type TrackerServerQuery,
  type TrackerServerSortDirection,
  type TrackerServerSortField,
  type TrackerStats,
} from "@/common/tracker";
import { capitalize, formatNumberWithCommas } from "@/common/utils";
import type { QueryFilterDefinition, QueryFilterOption } from "@/components/filters/types";
import { countryFlag, countryLabel, PROTOCOL_LABELS, protocolLabel } from "@/components/tracker/chart-utils";

/**
 * Proper casing for the server software names the tracker reports lowercased.
 * Anything missing falls back to a capitalized first letter.
 */
const PLATFORM_LABELS: Record<string, string> = {
  bungeecord: "BungeeCord",
  folia: "Folia",
  leaf: "Leaf",
  paper: "Paper",
  purpur: "Purpur",
  spigot: "Spigot",
  unknown: "Unknown",
  velocity: "Velocity",
  waterfall: "Waterfall",
};

function countryOptions(stats: TrackerStats | null | undefined): QueryFilterOption[] {
  return Object.entries(stats?.geo ?? {}).map(([code, count]) => ({
    value: code,
    label: countryLabel(code),
    glyph: countryFlag(code) ?? undefined,
    detail: `${formatNumberWithCommas(count)} servers`,
    keywords: code,
  }));
}

function platformOptions(stats: TrackerStats | null | undefined): QueryFilterOption[] {
  return Object.entries(stats?.platform ?? {}).map(([name, count]) => ({
    value: name,
    label: PLATFORM_LABELS[name] ?? capitalize(name),
    detail: `${formatNumberWithCommas(count)} servers`,
    keywords: name,
  }));
}

function protocolOptions(stats: TrackerStats | null | undefined): QueryFilterOption[] {
  const counts = stats?.protocol ?? {};
  const popular = Object.entries(counts).map(([protocol, count]) => ({
    value: protocol,
    label: protocolLabel(protocol),
    detail: `${formatNumberWithCommas(count)} servers`,
    keywords: protocol,
  }));
  const seen = new Set(Object.keys(counts));
  const rest = Object.entries(PROTOCOL_LABELS)
    .filter(([protocol]) => !seen.has(protocol))
    .map(([protocol, label]) => ({ value: protocol, label, keywords: protocol }));
  return [...popular, ...rest];
}

/**
 * Builds the browse-page filter panel.
 *
 * The tracker statistics supply the suggestions for the country, platform, and
 * protocol pickers; without them those fields degrade to plain text inputs that
 * still accept any value the API understands.
 */
export function buildTrackerServerFilterDefinition(stats?: TrackerStats | null): QueryFilterDefinition {
  return {
    title: "Filter servers",
    fields: [
      {
        name: "ip",
        label: "IP address",
        type: "text",
        group: "primary",
        icon: "network",
        placeholder: "198.51.100",
        description: "Prefix match on the address.",
      },
      {
        name: "country",
        label: "Location",
        type: "combobox",
        group: "primary",
        icon: "map-pin",
        placeholder: "Any country",
        description: "Two-letter ISO code, such as US or DE.",
        emptyMessage: "No matching country. Use an ISO code such as US or DE.",
        options: countryOptions(stats),
      },
      {
        name: "platform",
        label: "Platform",
        type: "combobox",
        group: "advanced",
        icon: "boxes",
        placeholder: "Any platform",
        options: platformOptions(stats),
      },
      {
        name: "protocol",
        label: "Protocol",
        type: "combobox",
        group: "advanced",
        icon: "signal",
        placeholder: "Any version",
        emptyMessage: "No matching version. A protocol number such as 769 also works.",
        wholeNumber: { min: 0, message: "Enter a protocol number, such as 769." },
        options: protocolOptions(stats),
      },
      {
        name: "minOnlinePlayers",
        label: "Minimum players",
        type: "number",
        group: "advanced",
        placeholder: "Any",
        min: 0,
        step: 1,
      },
      {
        name: "maxOnlinePlayers",
        label: "Maximum players",
        type: "number",
        group: "advanced",
        placeholder: "Any",
        min: 0,
        step: 1,
        notBelow: {
          name: "minOnlinePlayers",
          message: "The maximum cannot be below the minimum.",
        },
      },
    ],
    sort: {
      name: "sort",
      directionName: "direction",
      label: "Sort by",
      directionLabel: "Order",
      defaultValue: "lastUpdated",
      defaultDirection: "desc",
      options: [
        { value: "lastUpdated", label: "Recently updated" },
        { value: "onlineCount", label: "Online players" },
        { value: "latencyMs", label: "Latency" },
        { value: "protocol", label: "Protocol version" },
        { value: "ip", label: "IP address" },
        { value: "country", label: "Location" },
      ],
      directionOptions: [
        { value: "desc", label: "Descending" },
        { value: "asc", label: "Ascending" },
      ],
    },
  };
}

type SearchParams = Record<string, string | string[] | undefined>;

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function optionalNumber(value: string | undefined): number | undefined {
  if (value == null || value.trim() === "") {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function trackerServerQueryFromSearchParams(params: SearchParams): TrackerServerQuery {
  const sort = firstValue(params.sort) as TrackerServerSortField | undefined;
  const direction = firstValue(params.direction) as TrackerServerSortDirection | undefined;

  return {
    page: 1,
    ip: firstValue(params.ip),
    country: firstValue(params.country),
    platform: firstValue(params.platform),
    protocol: optionalNumber(firstValue(params.protocol)),
    minOnlinePlayers: optionalNumber(firstValue(params.minOnlinePlayers)),
    maxOnlinePlayers: optionalNumber(firstValue(params.maxOnlinePlayers)),
    sort,
    direction,
  };
}

export function trackerServerPageHref(params: SearchParams, page: number): string {
  const query = trackerServerQueryFromSearchParams(params);
  return `/servers/browse?${serializeTrackerServerQuery({ ...query, page })}`;
}
