"use client";

import { fetchTrackerStats, type TrackerStats } from "@/common/tracker";
import { capitalize } from "@/common/utils";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { useQuery } from "@tanstack/react-query";
import { countryFlag, countryLabel, protocolLabel } from "./chart-utils";
import PieBreakdown from "./pie-breakdown";
import TrackerStatCards from "./tracker-stat-cards";

export const TRACKER_STATS_QUERY_KEY = ["trackerStats"] as const;

function buildLabels(
  breakdown: Record<string, number>,
  format: (key: string) => string
): Record<string, string> {
  return Object.fromEntries(Object.keys(breakdown).map(key => [key, format(key)]));
}

function BreakdownCardHeader({ title }: { title: string }) {
  return (
    <CardHeader>
      <div className="flex w-full items-center justify-between gap-2">
        <span>{title}</span>
        <span className="bg-muted/50 text-muted-foreground rounded-full px-2 py-0.5 text-[10px] font-semibold">
          Top 10
        </span>
      </div>
    </CardHeader>
  );
}

export default function TrackerStatsDashboard({ initialStats }: { initialStats: TrackerStats }) {
  // The QueryProvider default polls every 60s; `initialData` keeps the server-rendered
  // snapshot visible until the first fresh response arrives.
  const { data: stats = initialStats, dataUpdatedAt } = useQuery({
    queryKey: TRACKER_STATS_QUERY_KEY,
    queryFn: () => fetchTrackerStats({ cache: "no-store" }),
    initialData: initialStats,
    staleTime: 0,
  });

  // Unfingerprinted servers dominate the platform breakdown; lump them into "Other platforms".
  const platform = Object.fromEntries(Object.entries(stats.platform).filter(([key]) => key !== "unknown"));

  const secondsSinceUpdate = Math.max(0, Math.round((Date.now() - dataUpdatedAt) / 1000));
  const updatedText =
    secondsSinceUpdate < 5
      ? "just now"
      : secondsSinceUpdate < 60
        ? `${secondsSinceUpdate} seconds ago`
        : `${Math.floor(secondsSinceUpdate / 60)} minutes ago`;

  return (
    <div className="flex w-full max-w-[980px] flex-col gap-4">
      <TrackerStatCards stats={stats} />

      <div className="grid w-full grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="w-full">
          <BreakdownCardHeader title="Platforms" />
          <CardContent>
            <PieBreakdown
              data={platform}
              labels={buildLabels(platform, capitalize)}
              centerLabel="servers tracked"
              emptyMessage="No platform data yet. The tracker is still discovering server software."
              grandTotal={stats.trackedServers}
              remainderLabel="Other platforms"
            />
          </CardContent>
        </Card>

        <Card className="w-full">
          <BreakdownCardHeader title="Protocol Versions" />
          <CardContent>
            <PieBreakdown
              data={stats.protocol}
              labels={buildLabels(stats.protocol, protocolLabel)}
              centerLabel="servers"
              emptyMessage="No protocol data yet. The tracker is still discovering server versions."
              grandTotal={stats.trackedServers}
              remainderLabel="Other protocols"
            />
          </CardContent>
        </Card>

        <Card className="w-full">
          <BreakdownCardHeader title="Geography" />
          <CardContent>
            <PieBreakdown
              data={stats.geo}
              labels={buildLabels(stats.geo, code => {
                const flag = countryFlag(code);
                return flag ? `${flag} ${countryLabel(code)}` : countryLabel(code);
              })}
              centerLabel="servers"
              emptyMessage="No geographic data yet. The tracker is still resolving server locations."
              grandTotal={stats.trackedServers}
              remainderLabel="Other countries"
            />
          </CardContent>
        </Card>
      </div>

      <p className="text-muted-foreground mt-4 text-center text-xs">
        Last updated {updatedText} · refreshed every minute.
      </p>
    </div>
  );
}
