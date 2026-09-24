import DetailRowsSkeleton from "@/components/skeleton/ui/detail-rows-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

function MetricSkeleton() {
  return (
    <div className="bg-muted/50 flex items-center gap-2.5 rounded-lg px-3 py-2.5">
      <Skeleton className="size-4 shrink-0 rounded" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <Skeleton className="h-4 w-20 rounded" />
        <Skeleton className="h-3 w-16 rounded" />
      </div>
    </div>
  );
}

function CardSkeleton({ titleWidth = "w-24", rows = 5 }: { titleWidth?: string; rows?: number }) {
  return (
    <Card className="h-full w-full min-w-0 overflow-hidden p-0">
      <CardHeader>
        <Skeleton className={`h-3 ${titleWidth} rounded`} />
      </CardHeader>
      <CardContent className="p-4 pt-3">
        <DetailRowsSkeleton count={rows} />
      </CardContent>
    </Card>
  );
}

export default function TrackedServerLoading() {
  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8" aria-live="polite" aria-busy="true">
      <TrackerPageHeader
        breadcrumbs={[{ label: "Browse servers", href: "/servers/browse" }, { label: "Server" }]}
        title="Loading server"
        description="Preparing the latest public server observations."
        active="browse"
      />

      <div className="flex w-full max-w-5xl flex-col gap-5">
        <Card className="overflow-hidden p-0">
          <CardContent className="p-5">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-3">
                <Skeleton className="size-2.5 shrink-0 rounded-full" />
                <div className="flex flex-col gap-1">
                  <Skeleton className="h-4 w-16 rounded" />
                  <Skeleton className="h-3 w-40 rounded" />
                </div>
              </div>
              <Skeleton className="h-3 w-32 rounded" />
            </div>

            <Separator className="my-4" />

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {Array.from({ length: 4 }).map((_, index) => (
                <MetricSkeleton key={index} />
              ))}
            </div>
          </CardContent>
        </Card>

        <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2">
          <CardSkeleton titleWidth="w-20" rows={4} />
          <CardSkeleton titleWidth="w-16" rows={3} />
        </div>

        <Card className="h-full w-full min-w-0 overflow-hidden p-0">
          <CardHeader>
            <Skeleton className="h-3 w-12 rounded" />
          </CardHeader>
          <CardContent className="p-4 pt-3">
            <Skeleton className="h-8 w-2/3 rounded" />
          </CardContent>
        </Card>

        <Card className="h-full w-full min-w-0 overflow-hidden p-0">
          <CardHeader>
            <Skeleton className="h-3 w-32 rounded" />
          </CardHeader>
          <CardContent className="p-4 pt-3">
            <div className="grid gap-3 sm:grid-cols-2">
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} className="h-9 w-full rounded-lg" />
              ))}
            </div>
            <Skeleton className="mt-4 h-3 w-72 max-w-full rounded" />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
