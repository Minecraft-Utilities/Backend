import SkinCardSkeleton from "@/components/skeleton/skins/skin-card-skeleton";
import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import { Button } from "@/components/ui/button";
import { SKIN_SORT_OPTIONS } from "./page";

export default function SkinsLoading() {
  return (
    <div
      className="mt-10 flex w-full flex-col items-center justify-center gap-10"
      aria-live="polite"
      aria-busy="true"
    >
      <header className="w-full max-w-[980px]">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">Minecraft Skins</h1>
        <p className="text-muted-foreground mt-2 text-sm">Discover thousands of Minecraft skins</p>
      </header>

      <div className="w-full max-w-[980px]">
        <div className="mb-6 flex flex-row justify-center gap-4 sm:justify-end">
          <div className="border-border bg-background/60 flex flex-wrap items-center justify-end gap-2 rounded-md border px-3 py-2 shadow-sm">
            {Object.entries(SKIN_SORT_OPTIONS).map(([key, label]) => (
              <Button key={key} variant="secondary" disabled>
                {label.icon}
                {label.label}
              </Button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-5">
          {Array.from({ length: 25 }).map((_, i) => (
            <SkinCardSkeleton key={i} />
          ))}
        </div>

        <div className="mt-6 flex justify-center">
          <PaginationSkeleton />
        </div>
      </div>
    </div>
  );
}
