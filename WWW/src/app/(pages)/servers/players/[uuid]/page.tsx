import {
  fetchTrackedPlayer,
  TRACKER_REVALIDATE_SECONDS,
  TrackerApiError,
  type TrackedPlayer,
} from "@/common/tracker";
import TrackerErrorCard from "@/components/tracker/tracker-error-card";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import TrackerPlayerSightings, { newestPlayerUsername } from "@/components/tracker/tracker-player-sightings";
import { Button } from "@/components/ui/button";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";

export const revalidate = 60;

type PlayerPageProps = {
  params: Promise<{ uuid: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

const loadPlayer = cache((rawUuid: string, page: number) => {
  let uuid: string;

  try {
    uuid = decodeURIComponent(rawUuid);
  } catch {
    throw new Error("Invalid player UUID.");
  }

  return fetchTrackedPlayer(uuid, page, { next: { revalidate: TRACKER_REVALIDATE_SECONDS } });
});

export async function generateMetadata(props: PlayerPageProps): Promise<Metadata> {
  const { uuid: rawUuid } = await props.params;

  try {
    const player = await loadPlayer(rawUuid, 1);
    const username = newestPlayerUsername(player.servers.items);
    const title = username ? `${username}'s server history` : "Player server history";
    const description = username
      ? `View ${username}'s public server sightings, first and last seen dates, and times observed on MC Utils.`
      : "View public server sightings and history on MC Utils.";

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
      title: "Player not found",
      description: "This player could not be found on MC Utils.",
      openGraph: {
        title: "Player not found",
        description: "This player could not be found on MC Utils.",
      },
    };
  }
}

export default async function TrackedPlayerPage(props: PlayerPageProps) {
  const { uuid: rawUuid } = await props.params;
  const searchParams = await props.searchParams;
  const rawPage = Array.isArray(searchParams.page) ? searchParams.page[0] : searchParams.page;
  const parsedPage = rawPage && /^[1-9]\d*$/.test(rawPage) ? Number(rawPage) : 1;
  const page = Number.isSafeInteger(parsedPage) ? parsedPage : 1;

  let player: TrackedPlayer;

  try {
    player = await loadPlayer(rawUuid, page);
  } catch (error) {
    if (error instanceof TrackerApiError && error.status === 404) {
      notFound();
    }

    return (
      <div className="mt-10 flex w-full flex-col items-center gap-8">
        <TrackerPageHeader
          breadcrumbs={[{ label: "Player history", href: "/servers/players" }, { label: "Player" }]}
          title="Player history unavailable"
          description="The requested player sightings could not be loaded."
          active="players"
        />
        <TrackerErrorCard
          title="Player history unavailable"
          message={
            error instanceof Error && error.message
              ? error.message
              : "Player server history could not be loaded."
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

  const username = newestPlayerUsername(player.servers.items);

  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8">
      <TrackerPageHeader
        breadcrumbs={[{ label: "Player history", href: "/servers/players" }, { label: "Player" }]}
        title={username ?? "Player"}
        description="Public server sightings and refresh history from MC Utils."
        active="players"
        actions={
          <>
            {username ? (
              <Button asChild variant="outline">
                <Link href={`/player/${encodeURIComponent(username)}`}>Player profile</Link>
              </Button>
            ) : null}
            <Button asChild>
              <Link href="/servers/browse">Browse servers</Link>
            </Button>
          </>
        }
      />
      <TrackerPlayerSightings
        player={player}
        currentPage={page}
        hrefForPage={targetPage =>
          `/servers/players/${encodeURIComponent(player.playerUuid)}?page=${targetPage}`
        }
      />
    </div>
  );
}
