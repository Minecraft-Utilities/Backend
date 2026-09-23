import {
  fetchTrackerServers,
  TRACKER_REVALIDATE_SECONDS,
  type TrackedServerSummary,
  type TrackerPage,
} from "@/common/tracker";
import TrackerErrorCard from "@/components/tracker/tracker-error-card";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import TrackerServerList from "@/components/tracker/tracker-server-list";
import { Button } from "@/components/ui/button";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Browse Minecraft Servers",
  description:
    "Browse public Minecraft servers with software, versions, locations, player counts, and freshness.",
};

export const revalidate = 60;

function normalizePage(value: string | string[] | undefined): number | null {
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw == null || raw === "") {
    return 1;
  }
  if (!/^[1-9]\d*$/.test(raw)) {
    return null;
  }

  const page = Number(raw);
  return Number.isSafeInteger(page) ? page : null;
}

export default async function BrowseTrackedServersPage({ searchParams }: PageProps<"/servers/browse">) {
  const params = await searchParams;
  const page = normalizePage(params.page);

  let servers: TrackerPage<TrackedServerSummary> | null = null;
  let loadError: string | null = null;

  if (page !== null) {
    try {
      servers = await fetchTrackerServers(page, { next: { revalidate: TRACKER_REVALIDATE_SECONDS } });
    } catch (error) {
      loadError = error instanceof Error ? error.message : "The tracked server list could not be loaded.";
    }
  }

  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8">
      <TrackerPageHeader
        title="Browse Minecraft servers"
        description="Explore public Minecraft servers ordered by their most recent successful observation."
        active="browse"
        actions={
          <Button asChild variant="outline">
            <Link href="/servers/players">Player history</Link>
          </Button>
        }
      />

      {page === null ? (
        <TrackerErrorCard
          title="Invalid page"
          message="Page numbers must be positive whole numbers."
          action={
            <Button asChild variant="outline">
              <Link href="/servers/browse?page=1">Go to page one</Link>
            </Button>
          }
        />
      ) : loadError || !servers ? (
        <TrackerErrorCard
          title="Servers unavailable"
          message={loadError ?? "The server list could not be loaded."}
          action={
            <Button asChild variant="outline">
              <Link href="/servers/browse?page=1">Return to page one</Link>
            </Button>
          }
        />
      ) : (
        <div className="flex w-full max-w-5xl flex-col gap-4">
          <p className="text-muted-foreground text-sm" aria-live="polite">
            Page {page} of {servers.totalPages || 1} · {servers.totalItems} public servers
          </p>
          <TrackerServerList
            data={servers}
            currentPage={page}
            hrefForPage={targetPage => `/servers/browse?page=${targetPage}`}
          />
        </div>
      )}
    </div>
  );
}
