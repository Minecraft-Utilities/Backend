import DetailRowsSkeleton from "@/components/skeleton/ui/detail-rows-skeleton";
import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

function SightingRowSkeleton() {
  return (
    <Card className="h-fit w-full min-w-0 overflow-hidden p-0">
      <CardContent className="flex flex-col gap-3 p-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-col gap-1.5">
            <Skeleton className="h-4 w-32 rounded" />
            <Skeleton className="h-3 w-44 rounded" />
          </div>
          <Skeleton className="h-8 w-28 rounded-md" />
        </div>
        <DetailRowsSkeleton count={4} labelWidth="short" />
      </CardContent>
    </Card>
  );
}

export default function TrackedPlayerLoading() {
  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8" aria-live="polite" aria-busy="true">
      <TrackerPageHeader
        eyebrow="Player history"
        title="Loading server history"
        description="Preparing public server sightings."
        active="browse"
      />

      <div className="flex w-full max-w-5xl flex-col gap-4">
        <Card className="h-fit w-full min-w-0 overflow-hidden p-0">
          <CardHeader>
            <Skeleton className="h-3 w-20 rounded" />
          </CardHeader>
          <CardContent className="p-3 pt-2">
            <DetailRowsSkeleton count={4} labelWidth="short" />
          </CardContent>
        </Card>

        <ul className="flex min-w-0 flex-col gap-3" aria-hidden>
          {Array.from({ length: 3 }).map((_, index) => (
            <li key={index} className="min-w-0">
              <SightingRowSkeleton />
            </li>
          ))}
        </ul>

        <PaginationSkeleton />
      </div>
    </div>
  );
}
