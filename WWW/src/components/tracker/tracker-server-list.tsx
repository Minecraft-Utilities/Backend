import type { TrackedServerSummary, TrackerPage } from "@/common/tracker";
import { formatNumberWithCommas } from "@/common/utils";
import TimeAgo from "@/components/time-ago";
import { countryFlag, countryLabel, protocolLabel } from "@/components/tracker/chart-utils";
import Card, { CardContent } from "@/components/ui/card";
import Pagination from "@/components/ui/pagination";
import { ArrowUpRight, WifiOff } from "lucide-react";
import Link from "next/link";

export interface TrackerServerListProps {
  data: TrackerPage<TrackedServerSummary>;
  currentPage?: number;
  limit?: number;
  showPagination?: boolean;
  hrefForPage?: (page: number) => string;
}

function ServerRow({ server }: { server: TrackedServerSummary }) {
  const address =
    server.ip.includes(":") && !server.ip.startsWith("[")
      ? `[${server.ip}]:${server.port}`
      : `${server.ip}:${server.port}`;
  const flag = server.country ? countryFlag(server.country) : null;
  const software = server.version ?? server.platform ?? "Version unknown";
  const protocol = server.protocol == null ? null : protocolLabel(String(server.protocol));

  return (
    <Link
      href={`/servers/${encodeURIComponent(server.uuid)}`}
      className="group hover:bg-accent/45 focus-visible:bg-accent/50 grid grid-cols-[minmax(0,1fr)_auto] gap-3 px-4 py-4 transition-colors focus-visible:outline-none md:grid-cols-[minmax(0,2fr)_8rem_10rem_9rem_auto] md:items-center"
      aria-label={`View tracked server ${address}`}
    >
      <div className="min-w-0">
        <div className="flex min-w-0 items-center gap-2.5">
          <span
            className={
              server.online
                ? "size-2 shrink-0 rounded-full bg-emerald-500 shadow-[0_0_0_3px_rgb(16_185_129_/_0.12)]"
                : "bg-muted-foreground/50 size-2 shrink-0 rounded-full"
            }
            aria-hidden
          />
          <span className="text-foreground group-hover:text-primary truncate font-semibold">{address}</span>
          <span className="text-muted-foreground text-xs md:hidden">
            {server.online ? "Online" : "Offline"}
          </span>
        </div>
        <p className="text-muted-foreground mt-1 truncate pl-[18px] text-xs">
          {software}
          {protocol ? ` · Minecraft ${protocol}` : ""}
        </p>
      </div>

      <div className="hidden md:block">
        <p className="text-foreground text-sm font-medium tabular-nums">
          {formatNumberWithCommas(server.onlineCount)} / {formatNumberWithCommas(server.maxPlayers)}
        </p>
        <p className="text-muted-foreground mt-0.5 text-[11px] uppercase">advertised</p>
      </div>

      <div className="hidden min-w-0 md:block">
        <p className="text-foreground truncate text-sm font-medium">
          {server.country ? (
            <>
              {flag ? <span aria-hidden>{flag} </span> : null}
              {countryLabel(server.country)}
            </>
          ) : (
            "Unknown"
          )}
        </p>
        <p className="text-muted-foreground mt-0.5 text-[11px] uppercase">location</p>
      </div>

      <div className="hidden text-right md:block">
        <p className="text-foreground text-sm font-medium">
          <TimeAgo date={new Date(server.lastUpdated)} />
        </p>
        <p className="text-muted-foreground mt-0.5 text-[11px] uppercase">last update</p>
      </div>

      <ArrowUpRight
        className="text-muted-foreground group-hover:text-primary size-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
        aria-hidden
      />
    </Link>
  );
}

export default function TrackerServerList({
  data,
  currentPage = 1,
  limit,
  showPagination = true,
  hrefForPage,
}: TrackerServerListProps) {
  const items = typeof limit === "number" ? data.items.slice(0, limit) : data.items;

  if (items.length === 0) {
    return (
      <Card className="w-full">
        <CardContent className="text-muted-foreground flex min-h-40 flex-col items-center justify-center gap-2 text-center">
          <WifiOff className="size-6" aria-hidden />
          <p className="text-foreground font-medium">No public servers to show</p>
          <p className="text-sm">The tracker has no server records for this page yet.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="flex w-full flex-col gap-6">
      <div className="border-border/70 bg-card/65 overflow-hidden rounded-xl border">
        <div className="text-muted-foreground border-border/60 hidden grid-cols-[minmax(0,2fr)_8rem_10rem_9rem_auto] gap-3 border-b px-4 py-2.5 text-[11px] font-medium tracking-wide uppercase md:grid">
          <span>Server</span>
          <span>Players</span>
          <span>Location</span>
          <span className="text-right">Updated</span>
          <span className="w-4" />
        </div>
        <ul className="divide-border/60 divide-y">
          {items.map(server => (
            <li key={server.uuid}>
              <ServerRow server={server} />
            </li>
          ))}
        </ul>
      </div>

      {showPagination && data.totalItems > 0 ? (
        <Pagination
          page={currentPage}
          totalItems={data.totalItems}
          itemsPerPage={data.itemsPerPage}
          basePath="/servers/browse"
          hrefForPage={hrefForPage}
        />
      ) : null}
    </div>
  );
}
