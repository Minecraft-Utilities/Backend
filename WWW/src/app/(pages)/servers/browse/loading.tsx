import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";

function ServerRowSkeleton() {
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-3 px-4 py-4 md:grid-cols-[minmax(0,2fr)_8rem_10rem_9rem_auto] md:items-center">
      <div className="flex min-w-0 flex-col gap-2">
        <Skeleton className="h-4 w-2/3 rounded" />
        <Skeleton className="h-3 w-1/2 rounded" />
      </div>
      <Skeleton className="size-4 rounded" />
      <Skeleton className="hidden h-3 w-16 rounded md:block" />
      <Skeleton className="hidden h-3 w-20 rounded md:block" />
      <Skeleton className="hidden h-3 w-16 rounded md:block" />
    </div>
  );
}

export default function BrowseTrackedServersLoading() {
  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8" aria-live="polite" aria-busy="true">
      <TrackerPageHeader
        title="Browse Minecraft servers"
        description="Loading the latest public server observations."
        active="browse"
      />

      <div className="flex w-full max-w-5xl flex-col gap-6">
        <div className="border-border/70 bg-card/65 overflow-hidden rounded-xl border">
          <div className="border-border/60 text-muted-foreground hidden grid-cols-[minmax(0,2fr)_8rem_10rem_9rem_auto] gap-3 border-b px-4 py-2.5 text-[11px] font-medium tracking-wide uppercase md:grid">
            <span>Server</span>
            <span>Players</span>
            <span>Location</span>
            <span className="text-right">Updated</span>
            <span className="w-4" />
          </div>
          <div className="divide-border/60 divide-y">
            {Array.from({ length: 10 }).map((_, index) => (
              <ServerRowSkeleton key={index} />
            ))}
          </div>
        </div>
        <PaginationSkeleton />
      </div>
    </div>
  );
}
