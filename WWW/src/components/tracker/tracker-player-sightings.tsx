import type { TrackedPlayer, TrackedPlayerServer } from "@/common/tracker";
import { formatNumberWithCommas } from "@/common/utils";
import { ProfileCopyableValue } from "@/components/profile-field";
import TimeAgo from "@/components/time-ago";
import { Button } from "@/components/ui/button";
import Card, { CardContent } from "@/components/ui/card";
import Pagination from "@/components/ui/pagination";
import { ArrowUpRight, CalendarClock } from "lucide-react";
import Link from "next/link";

export interface TrackerPlayerSightingsProps {
  player: TrackedPlayer;
  currentPage?: number;
  hrefForPage?: (page: number) => string;
}

function FreshnessValue({
  value,
  unavailable = "Not reported",
}: {
  value?: string | null;
  unavailable?: string;
}) {
  const date = value ? new Date(value) : null;
  if (!date || Number.isNaN(date.getTime()) || !value) {
    return <span className="text-muted-foreground">{unavailable}</span>;
  }

  return (
    <time dateTime={value} title={date.toLocaleString()} className="inline-flex items-center gap-1.5">
      <TimeAgo date={date} />
    </time>
  );
}

export function newestPlayerUsername(sightings: TrackedPlayerServer[]): string | null {
  let latest: TrackedPlayerServer | null = null;
  let latestTimestamp = Number.NEGATIVE_INFINITY;

  for (const sighting of sightings) {
    const timestamp = Date.parse(sighting.lastSeen);
    if (timestamp > latestTimestamp) {
      latest = sighting;
      latestTimestamp = timestamp;
    }
  }

  return latest?.username ?? null;
}

export function TrackerPlayerSightings({
  player,
  currentPage = 1,
  hrefForPage,
}: TrackerPlayerSightingsProps) {
  const sightings = player.servers.items;
  const totalTimesSeen = sightings.reduce((total, sighting) => total + sighting.timesSeen, 0);
  const firstSighting = sightings.reduce<string | null>((earliest, sighting) => {
    if (!earliest) {
      return sighting.firstSeen;
    }
    return Date.parse(sighting.firstSeen) < Date.parse(earliest) ? sighting.firstSeen : earliest;
  }, null);
  const latestSighting = sightings[0]?.lastSeen ?? null;
  const pageCount = Math.max(player.servers.totalPages, 1);
  const normalizedPage = Math.min(Math.max(currentPage, 1), pageCount);
  const pageHref =
    hrefForPage ??
    ((targetPage: number) => `/servers/players/${encodeURIComponent(player.playerUuid)}?page=${targetPage}`);

  return (
    <section className="flex w-full min-w-0 flex-col gap-6" aria-label="Tracked player sightings">
      <Card className="overflow-hidden p-0">
        <CardContent className="grid gap-5 p-5 md:grid-cols-[minmax(0,1.4fr)_repeat(3,minmax(7rem,0.6fr))] md:items-center">
          <div className="min-w-0">
            <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">Player UUID</p>
            <div className="mt-2">
              <ProfileCopyableValue text={player.playerUuid} />
            </div>
          </div>
          <div>
            <p className="text-foreground text-2xl font-semibold tabular-nums">
              {formatNumberWithCommas(player.servers.totalItems)}
            </p>
            <p className="text-muted-foreground text-xs">public servers</p>
          </div>
          <div>
            <p className="text-foreground text-2xl font-semibold tabular-nums">
              {formatNumberWithCommas(totalTimesSeen)}
            </p>
            <p className="text-muted-foreground text-xs">samples on this page</p>
          </div>
          <div>
            <p className="text-foreground text-sm font-medium">
              <FreshnessValue value={latestSighting} />
            </p>
            <p className="text-muted-foreground mt-1 text-xs">
              latest · first <FreshnessValue value={firstSighting} />
            </p>
          </div>
        </CardContent>
      </Card>

      {sightings.length === 0 ? (
        <Card className="w-full max-w-md">
          <CardContent className="flex flex-col items-center gap-2 text-center">
            <CalendarClock className="text-muted-foreground size-6" aria-hidden />
            <p className="text-foreground font-medium">No public sightings on this page</p>
            <p className="text-muted-foreground text-sm">
              Return to page one to see the full tracked-player history.
            </p>
          </CardContent>
        </Card>
      ) : (
        <ol
          className="border-border/60 ml-2 flex flex-col border-l pl-6"
          aria-label="Server sighting timeline"
        >
          {sightings.map((sighting, index) => {
            const address =
              sighting.server.ip.includes(":") && !sighting.server.ip.startsWith("[")
                ? `[${sighting.server.ip}]:${sighting.server.port}`
                : `${sighting.server.ip}:${sighting.server.port}`;

            return (
              <li key={`${sighting.server.uuid}-${index}`} className="relative pb-4 last:pb-0">
                <span
                  className={
                    sighting.server.online
                      ? "ring-background absolute top-5 -left-[31px] size-2.5 rounded-full bg-emerald-500 ring-4"
                      : "bg-muted-foreground/50 ring-background absolute top-5 -left-[31px] size-2.5 rounded-full ring-4"
                  }
                  aria-hidden
                />
                <Card className="hover:border-primary/35 overflow-hidden p-0 transition-colors">
                  <CardContent className="p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="text-foreground truncate font-semibold">{address}</p>
                        <p className="text-muted-foreground mt-1 truncate text-xs">
                          {sighting.server.version ?? sighting.server.platform ?? "Minecraft server"} ·
                          observed as {sighting.username}
                        </p>
                      </div>
                      <Button asChild variant="ghost" size="icon-sm" aria-label={`View ${address}`}>
                        <Link href={`/servers/${encodeURIComponent(sighting.server.uuid)}`}>
                          <ArrowUpRight className="size-4" aria-hidden />
                        </Link>
                      </Button>
                    </div>

                    <div className="border-border/60 mt-4 grid grid-cols-2 gap-4 border-t pt-4 sm:grid-cols-3">
                      <div>
                        <p className="text-foreground text-sm font-medium">
                          <FreshnessValue value={sighting.firstSeen} />
                        </p>
                        <p className="text-muted-foreground text-xs">first on server</p>
                      </div>
                      <div>
                        <p className="text-foreground text-sm font-medium">
                          <FreshnessValue value={sighting.lastSeen} />
                        </p>
                        <p className="text-muted-foreground text-xs">last on server</p>
                      </div>
                      <div>
                        <p className="text-foreground text-sm font-medium tabular-nums">
                          {formatNumberWithCommas(sighting.timesSeen)}
                        </p>
                        <p className="text-muted-foreground text-xs">times seen</p>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </li>
            );
          })}
        </ol>
      )}

      {player.servers.totalItems > 0 ? (
        <Pagination
          page={normalizedPage}
          totalItems={player.servers.totalItems}
          itemsPerPage={player.servers.itemsPerPage}
          basePath={`/servers/players/${encodeURIComponent(player.playerUuid)}`}
          hrefForPage={pageHref}
        />
      ) : null}
    </section>
  );
}

export default TrackerPlayerSightings;
