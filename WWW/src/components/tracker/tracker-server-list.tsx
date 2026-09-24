import type { TrackedServerSummary, TrackerPage } from "@/common/tracker";
import { serverAddress } from "@/common/tracker";
import { formatNumberWithCommas } from "@/common/utils";
import TimeAgo from "@/components/time-ago";
import { countryFlag, countryLabel, protocolLabel } from "@/components/tracker/chart-utils";
import Card from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import Pagination from "@/components/ui/pagination";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
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
  const address = serverAddress(server);
  const flag = server.country ? countryFlag(server.country) : null;
  const software = server.version ?? server.platform ?? "Version unknown";
  const protocol = server.protocol == null ? null : protocolLabel(String(server.protocol));

  return (
    <TableRow className="group hover:bg-accent/40 relative">
      <TableCell className="w-full max-w-0 px-4 py-3">
        <Link
          href={`/servers/${encodeURIComponent(server.uuid)}`}
          className="focus-visible:ring-ring/50 absolute inset-0 z-10 rounded-sm focus-visible:ring-[3px] focus-visible:outline-none"
          aria-label={`View tracked server ${address}`}
        />
        <div className="flex min-w-0 items-center gap-2.5">
          <span
            className={
              server.online
                ? "size-2 shrink-0 rounded-full bg-emerald-500"
                : "bg-muted-foreground/50 size-2 shrink-0 rounded-full"
            }
            aria-hidden
          />
          <span className="text-foreground group-hover:text-primary truncate font-medium">{address}</span>
          <span className="text-muted-foreground text-xs md:hidden">
            {server.online ? "Online" : "Offline"}
          </span>
        </div>
        <p className="text-muted-foreground mt-0.5 truncate pl-[18px] text-xs">
          {software}
          {protocol ? ` · ${protocol}` : ""}
        </p>
      </TableCell>

      <TableCell className="text-foreground hidden px-4 py-3 tabular-nums md:table-cell">
        {formatNumberWithCommas(server.onlineCount)} / {formatNumberWithCommas(server.maxPlayers)}
      </TableCell>

      <TableCell className="text-muted-foreground hidden max-w-56 truncate px-4 py-3 md:table-cell">
        {server.country ? (
          <>
            {flag ? <span aria-hidden>{flag} </span> : null}
            {countryLabel(server.country)}
          </>
        ) : (
          "Unknown"
        )}
      </TableCell>

      <TableCell className="text-muted-foreground hidden px-4 py-3 text-right whitespace-nowrap md:table-cell">
        <TimeAgo date={new Date(server.lastUpdated)} />
      </TableCell>

      <TableCell className="hidden w-10 px-2 py-3 md:table-cell">
        <ArrowUpRight
          className="text-muted-foreground group-hover:text-primary size-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
          aria-hidden
        />
      </TableCell>
    </TableRow>
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
      <Card className="w-full overflow-hidden p-0">
        <Empty className="min-h-48">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <WifiOff aria-hidden />
            </EmptyMedia>
            <EmptyTitle>No public servers to show</EmptyTitle>
            <EmptyDescription>The tracker has no server records for this page yet.</EmptyDescription>
          </EmptyHeader>
        </Empty>
      </Card>
    );
  }

  return (
    <div className="flex w-full flex-col gap-6">
      <Card className="overflow-hidden p-0">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="text-muted-foreground h-11 px-4">Server</TableHead>
              <TableHead className="text-muted-foreground hidden px-4 md:table-cell">Players</TableHead>
              <TableHead className="text-muted-foreground hidden px-4 md:table-cell">Location</TableHead>
              <TableHead className="text-muted-foreground hidden px-4 text-right md:table-cell">
                Updated
              </TableHead>
              <TableHead className="hidden w-10 px-2 md:table-cell">
                <span className="sr-only">Open server</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map(server => (
              <ServerRow key={server.uuid} server={server} />
            ))}
          </TableBody>
        </Table>
      </Card>

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
