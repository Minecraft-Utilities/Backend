import Skeleton from "@/components/skeleton/ui/skeleton";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

function PieCardSkeleton() {
  return (
    <Card className="w-full">
      <CardHeader>
        <Skeleton className="h-3 w-24 rounded" />
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-6">
        <Skeleton className="size-48 shrink-0 rounded-full" />
        <div className="flex w-full min-w-0 flex-col gap-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="flex items-center gap-2">
              <Skeleton className="size-2.5 shrink-0 rounded-full" />
              <Skeleton className="h-4 flex-1 rounded" />
              <Skeleton className="h-4 w-16 rounded" />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function RecentServerRowSkeleton() {
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

export default function TrackerStatsLoading() {
  return (
    <div
      className="mt-10 flex w-full flex-col items-center justify-center gap-10"
      aria-live="polite"
      aria-busy="true"
    >
      <TrackerPageHeader
        title="Minecraft Server Tracker"
        description="Loading live statistics and recently observed public Minecraft servers."
        active="overview"
      />

      <div className="flex w-full max-w-[980px] flex-col gap-4">
        <Card className="overflow-hidden p-0">
          <CardContent className="divide-border/60 grid grid-cols-1 divide-y p-0 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="flex flex-col gap-2 px-5 py-5">
                <Skeleton className="h-3 w-24 rounded" />
                <Skeleton className="h-8 w-28 rounded" />
                <Skeleton className="h-3 w-32 rounded" />
              </div>
            ))}
          </CardContent>
        </Card>

        <div className="grid w-full grid-cols-1 gap-4 lg:grid-cols-3">
          <PieCardSkeleton />
          <PieCardSkeleton />
          <PieCardSkeleton />
        </div>
      </div>

      <section className="flex w-full max-w-[980px] flex-col gap-4">
        <div>
          <Skeleton className="h-7 w-44 rounded" />
          <Skeleton className="mt-2 h-3 w-72 max-w-full rounded" />
        </div>
        <div className="border-border/70 bg-card/65 overflow-hidden rounded-xl border">
          <div className="border-border/60 text-muted-foreground hidden grid-cols-[minmax(0,2fr)_8rem_10rem_9rem_auto] gap-3 border-b px-4 py-2.5 text-[11px] font-medium tracking-wide uppercase md:grid">
            <span>Server</span>
            <span>Players</span>
            <span>Location</span>
            <span className="text-right">Updated</span>
            <span className="w-4" />
          </div>
          <div className="divide-border/60 divide-y">
            {Array.from({ length: 6 }).map((_, index) => (
              <RecentServerRowSkeleton key={index} />
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
