import { fetchTrackerServers, fetchTrackerStats, TRACKER_REVALIDATE_SECONDS } from "@/common/tracker";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import TrackerServerList from "@/components/tracker/tracker-server-list";
import TrackerStatsDashboard from "@/components/tracker/tracker-stats-dashboard";
import { Button } from "@/components/ui/button";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Minecraft Server Tracker",
  description:
    "Explore public Minecraft servers, live statistics, recent observations, player samples, software, and geography.",
};

export default async function TrackerOverviewPage() {
  const options = { next: { revalidate: TRACKER_REVALIDATE_SECONDS } };
  const [stats, recentServers] = await Promise.all([
    fetchTrackerStats(options),
    fetchTrackerServers({ page: 1 }, options),
  ]);

  return (
    <div className="mt-10 flex w-full flex-col items-center gap-10">
      <TrackerPageHeader
        title="Minecraft Server Tracker"
        description="A live view of public Minecraft servers observed by MC Utils."
        active="overview"
        actions={
          <Button asChild variant="outline">
            <Link href="/servers/players">Player history</Link>
          </Button>
        }
      />

      <TrackerStatsDashboard initialStats={stats} />

      <section className="flex w-full max-w-5xl flex-col gap-4">
        <div>
          <h2 className="text-foreground text-2xl font-bold tracking-tight">Recently updated</h2>
          <p className="text-muted-foreground mt-1 text-sm">
            The latest public server observations from the tracker refresh cycle.
          </p>
        </div>
        <TrackerServerList data={recentServers} limit={6} showPagination={false} />
      </section>
    </div>
  );
}
