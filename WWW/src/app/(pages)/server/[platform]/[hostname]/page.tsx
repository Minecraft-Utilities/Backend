import { mcUtilsApi } from "@/common/mc-utils";
import { capitalize, formatNumberWithCommas } from "@/common/utils";
import Background from "@/components/background";
import { ServerDetails } from "@/components/server/server-details";
import ServerDnsRecords from "@/components/server/server-dns-records";
import ServerHeader from "@/components/server/server-header";
import ServerMotd from "@/components/server/server-motd";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { ServerPlatform } from "mcutils-js-api/dist/types/server/server";
import { Metadata } from "next";
import { cache } from "react";

export const dynamic = "force-dynamic";

const fetchServerCached = cache((hostname: string, edition: ServerPlatform) =>
  mcUtilsApi.fetchServer(hostname, edition)
);

async function getServer(platform: string, hostname: string) {
  const edition = platform.toLowerCase() as ServerPlatform;

  const { server, error } = await fetchServerCached(decodeURIComponent(hostname), edition);
  return { server, error, edition };
}

export async function generateMetadata(props: PageProps<"/server/[platform]/[hostname]">): Promise<Metadata> {
  const { platform, hostname } = await props.params;
  const { server, error, edition } = await getServer(platform, hostname);

  if (error || !server) {
    return {
      title: "Server not found",
      description: "This Minecraft server could not be found or is currently offline.",
      openGraph: {
        title: "Server not found",
        description: "This Minecraft server could not be found or is currently offline.",
      },
    };
  }
  const favicon = "favicon" in server ? server.favicon?.url : undefined;
  const players = server.players;
  const serverName = server.registryEntry?.displayName ?? server.hostname;
  const editionLabel = capitalize(edition!);

  return {
    title: `${serverName}: ${editionLabel} Minecraft Server`,
    description: `${serverName} has ${formatNumberWithCommas(players.online)}/${formatNumberWithCommas(players.max)} players online. View server status, MOTD, and details on MC Utils.`,
    icons: {
      ...(favicon ? { icon: favicon } : {}),
    },
    openGraph: {
      title: `${serverName}: ${editionLabel} Minecraft Server`,
      description: `${serverName} has ${formatNumberWithCommas(players.online)}/${formatNumberWithCommas(players.max)} players online. View server status, MOTD, and details on MC Utils.`,
      ...(favicon ? { images: [{ url: favicon }] } : {}),
    },
  };
}

export default async function ServerPage({ params }: PageProps<"/server/[platform]/[hostname]">) {
  const { platform, hostname } = await params;
  const { server, error, edition } = await getServer(platform, hostname);
  const serverBackground = server?.registryEntry
    ? server.registryEntry.backgroundImageUrl
    : "/media/backgrounds/server.webp";

  return (
    <>
      <Background url={serverBackground} />
      <div className="mt-16 flex w-full flex-col items-center gap-6">
        {(error || !server) && (
          <Card className="border-destructive/50 bg-destructive/10 w-full max-w-xl overflow-hidden p-0">
            <CardHeader variant="destructive">Error</CardHeader>
            <CardContent>
              <p className="text-muted-foreground text-sm">{error?.message ?? "Invalid lookup parameters"}</p>
            </CardContent>
          </Card>
        )}

        {server && (
          <div className="flex w-full max-w-3xl flex-col gap-16">
            {/* Header */}
            <ServerHeader server={server} edition={edition} />

            <div className="flex flex-col gap-4">
              {/* MOTD preview (Java only) */}
              <ServerMotd server={server} edition={edition} />

              {/* Details */}
              <ServerDetails server={server} edition={edition} />

              {/* DNS records (collapsible) */}
              <ServerDnsRecords records={server.records} />
            </div>
          </div>
        )}
      </div>
    </>
  );
}
