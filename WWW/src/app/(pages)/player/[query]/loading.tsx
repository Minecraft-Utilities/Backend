import PlayerCapesCardSkeleton from "@/components/skeleton/player/player-capes-card-skeleton";
import PlayerDetailsSkeleton from "@/components/skeleton/player/player-details-skeleton";
import PlayerHeaderSkeleton from "@/components/skeleton/player/player-header-skeleton";
import PlayerSkinCardSkeleton from "@/components/skeleton/player/player-skin-card-skeleton";
import PlayerSkinsCardSkeleton from "@/components/skeleton/player/player-skins-card-skeleton";
import PlayerUsernameHistorySkeleton from "@/components/skeleton/player/player-username-history-skeleton";

export default function PlayerLoading() {
  return (
    <div className="mt-10 flex w-full flex-col items-center gap-10" aria-live="polite" aria-busy="true">
      <div className="flex w-full max-w-[980px] flex-col gap-10">
        <PlayerHeaderSkeleton />

        <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
          <section className="flex w-full flex-col gap-4 lg:w-90 lg:shrink-0">
            <PlayerSkinCardSkeleton />
            <PlayerSkinsCardSkeleton />
            <PlayerCapesCardSkeleton />
          </section>

          <aside className="flex min-w-0 flex-1 flex-col gap-4">
            <PlayerDetailsSkeleton />
            <PlayerUsernameHistorySkeleton />
          </aside>
        </div>
      </div>
    </div>
  );
}
