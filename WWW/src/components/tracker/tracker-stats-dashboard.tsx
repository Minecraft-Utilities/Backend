"use client";

import { fetchTrackerStats, type TrackerStats } from "@/common/tracker";
import { capitalize } from "@/common/utils";
import { Badge } from "@/components/ui/badge";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { countryFlag, countryLabel } from "./chart-utils";
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
        <Badge variant="secondary">Top 10</Badge>
      </div>
    </CardHeader>
  );
}

function describeElapsed(seconds: number): string {
  if (seconds < 5) {
    return "just now";
  }
  if (seconds < 60) {
    return `${seconds} seconds ago`;
  }
  return `${Math.floor(seconds / 60)} minutes ago`;
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

  const [elapsedSeconds, setElapsedSeconds] = useState(0);

  // The clock is only read inside this effect so that render stays pure; the first
  // paint before the effect runs reads as "just now".
  useEffect(() => {
    const refresh = () => {
      setElapsedSeconds(Math.max(0, Math.round((Date.now() - dataUpdatedAt) / 1000)));
    };
    refresh();
    const timer = setInterval(refresh, 1000);
    return () => clearInterval(timer);
  }, [dataUpdatedAt]);

  // Unfingerprinted servers dominate the platform breakdown; lump them into "Other platforms".
  const platform = Object.fromEntries(Object.entries(stats.platform).filter(([key]) => key !== "unknown"));

  return (
    <div className="flex w-full max-w-5xl flex-col gap-4">
      <TrackerStatCards stats={stats} />

      <div className="grid w-full grid-cols-1 gap-4 lg:grid-cols-2">
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
        Last updated {describeElapsed(elapsedSeconds)} · refreshed every minute.
      </p>
    </div>
  );
}
