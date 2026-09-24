import {
  fetchTrackedServer,
  serverAddress,
  TRACKER_REVALIDATE_SECONDS,
  TrackerApiError,
  type TrackedServerDetail,
} from "@/common/tracker";
import TrackerErrorCard from "@/components/tracker/tracker-error-card";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import TrackerServerDetail from "@/components/tracker/tracker-server-detail";
import { Button } from "@/components/ui/button";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";

export const revalidate = 60;

type ServerPageProps = {
  params: Promise<{ uuid: string }>;
};

const loadServer = cache((rawUuid: string) => {
  let uuid: string;

  try {
    uuid = decodeURIComponent(rawUuid);
  } catch {
    throw new Error("Invalid server UUID.");
  }

  return fetchTrackedServer(uuid, { next: { revalidate: TRACKER_REVALIDATE_SECONDS } });
});

export async function generateMetadata(props: ServerPageProps): Promise<Metadata> {
  const { uuid: rawUuid } = await props.params;

  try {
    const server = await loadServer(rawUuid);
    const address = serverAddress(server);
    const label = server.version ? `${address} — ${server.version}` : address;
    const title = `${label} — Minecraft Server`;
    const description = `View ${label}'s server status, player counts, freshness, network details, and secure-chat capabilities on MC Utils.`;

    return {
      title,
      description,
      openGraph: {
        title,
        description,
      },
    };
  } catch {
    return {
      title: "Minecraft server not found",
      description: "This server could not be found on MC Utils.",
      openGraph: {
        title: "Minecraft server not found",
        description: "This server could not be found on MC Utils.",
      },
    };
  }
}

export default async function TrackedServerPage(props: ServerPageProps) {
  const { uuid: rawUuid } = await props.params;

  let server: TrackedServerDetail;

  try {
    server = await loadServer(rawUuid);
  } catch (error) {
    if (error instanceof TrackerApiError && error.status === 404) {
      notFound();
    }

    return (
      <div className="mt-10 flex w-full flex-col items-center gap-8">
        <TrackerPageHeader
          breadcrumbs={[{ label: "Browse servers", href: "/servers/browse" }, { label: "Server" }]}
          title="Server unavailable"
          description="The requested server could not be loaded."
          active="browse"
        />
        <TrackerErrorCard
          title="Server unavailable"
          message={
            error instanceof Error && error.message ? error.message : "Server details could not be loaded."
          }
          action={
            <Button asChild variant="outline" size="lg">
              <Link href="/servers/browse">Browse servers</Link>
            </Button>
          }
        />
      </div>
    );
  }

  const address = serverAddress(server);
  const software = server.version ?? server.platform ?? "Minecraft server";

  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8">
      <TrackerPageHeader
        breadcrumbs={[{ label: "Browse servers", href: "/servers/browse" }, { label: "Server" }]}
        title={address}
        description={`${software} server · latest public observations and refresh history.`}
        active="browse"
        actions={
          <Button asChild variant="outline">
            <Link href="/servers/browse">Browse servers</Link>
          </Button>
        }
      />
      <TrackerServerDetail server={server} />
    </div>
  );
}
