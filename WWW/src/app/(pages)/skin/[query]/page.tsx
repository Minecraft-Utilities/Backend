import { mcUtilsApi } from "@/common/mc-utils";
import SimpleLink from "@/components/simple-link";
import SkinDetails from "@/components/skin/skin-details";
import SkinHeadCommands from "@/components/skin/skin-head-commands";
import SkinPlayers from "@/components/skin/skin-players";
import SkinPreview from "@/components/skin/skin-preview";
import { Metadata } from "next";
import { redirect } from "next/navigation";

export const dynamic = "force-dynamic";

export async function generateMetadata(props: PageProps<"/skin/[query]">): Promise<Metadata> {
  const { query } = await props.params;
  const skinResponse = await mcUtilsApi.fetchSkin(query);
  const skin = skinResponse.skin;

  if (!skin) {
    return {
      title: "Skin not found",
      description: "Skin not found",
      openGraph: {
        title: "Skin not found",
        description: "Skin not found",
      },
    };
  }

  const favicon = skin.parts.HEAD_ISO;
  return {
    title: `${skin.firstSeenUsing}'s Minecraft Skin`,
    description: `View ${skin.firstSeenUsing}'s Minecraft skin on MC Utils: preview the full body, download, and see which players have used it.`,
    icons: {
      icon: favicon,
    },
    openGraph: {
      title: `${skin.firstSeenUsing}'s Minecraft Skin`,
      description: `View ${skin.firstSeenUsing}'s Minecraft skin on MC Utils: preview the full body, download, and see which players have used it.`,
      images: [
        {
          url: favicon,
        },
      ],
    },
  };
}

export default async function SkinsPage({ params }: PageProps<"/skin/[query]">) {
  const { query } = await params;

  const skinResponse = await mcUtilsApi.fetchSkin(query);
  const skin = skinResponse.skin;

  if (!skin) {
    return redirect("/skins");
  }

  return (
    <div className="mt-10 flex w-full flex-col items-center justify-center gap-10">
      <header className="w-full max-w-[980px] text-left">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">
          <span className="block sm:inline">
            <SimpleLink href={`/player/${skin.firstSeenUsing}`}>{skin.firstSeenUsing}'s Skin</SimpleLink>
          </span>
        </h1>
        <p className="text-muted-foreground mt-2 text-sm">
          Preview the full skin, download it, and see which players have worn it.
        </p>
      </header>

      <div className="flex w-full max-w-[980px] flex-col gap-4 lg:flex-row">
        {/* Left: preview + details */}
        <div className="flex w-full min-w-0 flex-1 flex-col gap-4">
          <SkinPreview skin={skin} />
          <SkinDetails skin={skin} />
        </div>

        {/* Right: players + commands */}
        <div className="flex w-full min-w-0 flex-col gap-4 lg:max-w-xl">
          <SkinPlayers skin={skin} />
          <SkinHeadCommands skin={skin} />
        </div>
      </div>
    </div>
  );
}
