import { env } from "./env";

/** One fixed-size page returned by the public server tracker. */
export interface TrackerPage<T> {
  items: T[];
  totalItems: number;
  itemsPerPage: number;
  totalPages: number;
}
/** Sort fields accepted by the public server tracker. */
export const TRACKER_SERVER_SORT_FIELDS = [
  "lastUpdated",
  "onlineCount",
  "latencyMs",
  "protocol",
  "ip",
  "country",
] as const;

export type TrackerServerSortField = (typeof TRACKER_SERVER_SORT_FIELDS)[number];

/** Sort directions accepted by the public server tracker. */
export type TrackerServerSortDirection = "asc" | "desc";

/** Query state accepted by the public server tracker endpoint. */
export interface TrackerServerQuery {
  page: number;
  ip?: string;
  country?: string;
  platform?: string;
  protocol?: number;
  minOnlinePlayers?: number;
  maxOnlinePlayers?: number;
  /**
   * `lastUpdated` | `onlineCount` | `latencyMs` | `protocol` | `ip` | `country`
   * are the known values. Invalid hand-edited URL values are forwarded
   * to the API as 400s instead of being silently rewritten.
   */
  sort?: TrackerServerSortField | (string & {});
  /** `asc` | `desc` are the known values. Invalid values are forwarded as 400s. */
  direction?: TrackerServerSortDirection | (string & {});
}

/** Serializes tracker query state using the public endpoint's parameter names. */
export function serializeTrackerServerQuery(query: TrackerServerQuery): string {
  const params = new URLSearchParams();
  params.set("page", String(query.page));

  const values: Array<[string, string | number | undefined]> = [
    ["ip", query.ip],
    ["country", query.country],
    ["platform", query.platform],
    ["protocol", query.protocol],
    ["minOnlinePlayers", query.minOnlinePlayers],
    ["maxOnlinePlayers", query.maxOnlinePlayers],
    ["sort", query.sort],
    ["direction", query.direction],
  ];

  for (const [key, value] of values) {
    if (value === undefined || value === null || (typeof value === "number" && !Number.isFinite(value))) {
      continue;
    }

    const serialized = String(value);
    if (serialized.length > 0) {
      params.set(key, serialized);
    }
  }

  return params.toString();
}

/** Compact public representation of a tracked server. */
export interface TrackedServerSummary {
  uuid: string;
  ip: string;
  port: number;
  version?: string | null;
  protocol?: number | null;
  platform?: string | null;
  /** Player count advertised by the server. */
  onlineCount: number;
  /** Player capacity advertised by the server. */
  maxPlayers: number;
  country?: string | null;
  /** Whether the tracker's latest refresh attempt succeeded. */
  online: boolean;
  /** ISO-8601 timestamp of the latest successful status fetch. */
  lastUpdated: string;
}

/** `ip:port` for a tracked server, bracketing the host when it is an IPv6 literal. */
export function serverAddress(server: Pick<TrackedServerSummary, "ip" | "port">): string {
  return server.ip.includes(":") && !server.ip.startsWith("[")
    ? `[${server.ip}]:${server.port}`
    : `${server.ip}:${server.port}`;
}

/** Full public representation of a tracked server. */
export interface TrackedServerDetail extends TrackedServerSummary {
  firstSeen: string;
  lastCheckedAt: string;
  motd?: string | null;
  latencyMs?: number | null;
  modded: boolean;
  preventsChatReports: boolean;
  enforcesSecureChat: boolean;
  previewsChat: boolean;
  asn?: number | null;
}

/** One public sighting of a tracked player on a server. */
export interface TrackedPlayerServer {
  username: string;
  firstSeen: string;
  lastSeen: string;
  timesSeen: number;
  server: TrackedServerSummary;
}

/** A tracked player and their paginated public server sightings. */
export interface TrackedPlayer {
  playerUuid: string;
  servers: TrackerPage<TrackedPlayerServer>;
}

/** Username-search result for a player with public tracker history. */
export interface TrackedPlayerSearchResult {
  playerUuid: string;
  username: string;
  skinId: number;
}

/** Error returned by the public server tracker API. */
export class TrackerApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "TrackerApiError";
    this.status = status;
  }
}

/** Fetch options, extended with Next's server-side cache controls. */
export interface TrackerFetchOptions extends RequestInit {
  next?: { revalidate?: number };
}

/** The public tracker API and its successful responses are cached for one minute. */
export const TRACKER_REVALIDATE_SECONDS = 60;

/**
 * Public statistics of the internet server tracker.
 *
 * Mirrors the `TrackerStatsResponse` DTO of the backend API:
 * https://mc.fascinated.cc/api/tracker/stats
 */
export interface TrackerStats {
  /** Total public servers in the tracker snapshot. */
  trackedServers: number;
  /** Distinct players ever observed on public tracked servers. */
  trackedPlayers: number;
  /** Distinct players observed in the latest eligible samples from reachable public servers. */
  onlinePlayers: number;
  /** Top-10 country ISO code -> server count */
  geo: Record<string, number>;
  /** Top-10 server-software (lowercased) -> server count, plain versions bucketed as `unknown` */
  platform: Record<string, number>;
  /** Top-10 status-protocol number -> server count */
  protocol: Record<string, number>;
}

const TRACKER_API_PATH = `${env.NEXT_PUBLIC_API_URL.replace(/\/$/, "")}/tracker`;

async function fetchTrackerJson<T>(
  path: string,
  resource: string,
  options?: TrackerFetchOptions
): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${TRACKER_API_PATH}${path}`, options);
  } catch (error) {
    const detail = error instanceof Error && error.message ? ` (${error.message})` : "";
    throw new TrackerApiError(`Unable to reach the server tracker while loading ${resource}${detail}.`, 0);
  }

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    if (!response.ok) {
      throw new TrackerApiError(
        `The server tracker returned HTTP ${response.status} while loading ${resource}.`,
        response.status
      );
    }
    throw new TrackerApiError(
      `The server tracker returned invalid JSON while loading ${resource}.`,
      response.status
    );
  }

  if (!response.ok) {
    const message =
      typeof payload === "object" &&
      payload !== null &&
      "message" in payload &&
      typeof payload.message === "string" &&
      payload.message.trim().length > 0
        ? payload.message
        : `The server tracker returned HTTP ${response.status} while loading ${resource}.`;
    throw new TrackerApiError(message, response.status);
  }

  return payload as T;
}

/**
 * Fetches the current public tracker statistics snapshot from the API.
 *
 * @param options optional fetch and Next cache controls
 * @returns the parsed statistics
 * @throws TrackerApiError when the endpoint cannot be reached or returns an error
 */
export async function fetchTrackerStats(options?: TrackerFetchOptions): Promise<TrackerStats> {
  return fetchTrackerJson<TrackerStats>("/stats", "tracker statistics", options);
}

/**
 * Fetches a fixed-size page of public tracked servers.
 *
 * @param query optional filter, sort, and pagination state; defaults to page one
 * @param options optional fetch and Next cache controls
 * @returns the parsed page of tracked server summaries
 * @throws TrackerApiError when the endpoint cannot be reached or returns an error
 */
export async function fetchTrackerServers(
  query: TrackerServerQuery = { page: 1 },
  options?: TrackerFetchOptions
): Promise<TrackerPage<TrackedServerSummary>> {
  return fetchTrackerJson<TrackerPage<TrackedServerSummary>>(
    `/servers?${serializeTrackerServerQuery(query)}`,
    "tracked servers",
    options
  );
}

/** Fetches the full public telemetry for one tracked server. */
export async function fetchTrackedServer(
  uuid: string,
  options?: TrackerFetchOptions
): Promise<TrackedServerDetail> {
  return fetchTrackerJson<TrackedServerDetail>(
    `/${encodeURIComponent(uuid)}`,
    "tracked server details",
    options
  );
}

/** Searches tracked players by username prefix. */
export async function searchTrackedPlayers(
  query: string,
  options?: TrackerFetchOptions
): Promise<TrackedPlayerSearchResult[]> {
  return fetchTrackerJson<TrackedPlayerSearchResult[]>(
    `/players?query=${encodeURIComponent(query)}`,
    "tracked player search",
    options
  );
}

/** Fetches a fixed-size page of public sightings for one tracked player. */
export async function fetchTrackedPlayer(
  uuid: string,
  page: number,
  options?: TrackerFetchOptions
): Promise<TrackedPlayer> {
  return fetchTrackerJson<TrackedPlayer>(
    `/players/${encodeURIComponent(uuid)}?page=${encodeURIComponent(page)}`,
    "tracked player sightings",
    options
  );
}
