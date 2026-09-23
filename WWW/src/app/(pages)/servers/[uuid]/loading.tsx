import DetailRowsSkeleton from "@/components/skeleton/ui/detail-rows-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

function CardSkeleton({ titleWidth = "w-24", rows = 5 }: { titleWidth?: string; rows?: number }) {
  return (
    <Card className="h-fit w-full min-w-0 overflow-hidden p-0">
      <CardHeader>
        <Skeleton className={`h-3 ${titleWidth} rounded`} />
      </CardHeader>
      <CardContent className="p-3 pt-2">
        <DetailRowsSkeleton count={rows} />
      </CardContent>
    </Card>
  );
}

export default function TrackedServerLoading() {
  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8" aria-live="polite" aria-busy="true">
      <TrackerPageHeader
        eyebrow="Minecraft server"
        title="Loading server"
        description="Preparing the latest public server observations."
        active="browse"
      />

      <div className="flex w-full max-w-5xl flex-col gap-4">
        <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2">
          <Card className="h-fit w-full min-w-0 overflow-hidden p-0">
            <CardHeader>
              <Skeleton className="h-3 w-16 rounded" />
            </CardHeader>
            <CardContent className="p-3 pt-2">
              <div className="flex flex-col gap-4">
                <Skeleton className="h-16 w-full rounded-xl" />
                <DetailRowsSkeleton count={5} />
              </div>
            </CardContent>
          </Card>
          <CardSkeleton titleWidth="w-20" rows={5} />
        </div>

        <Card className="h-fit w-full min-w-0 overflow-hidden p-0">
          <CardHeader>
            <Skeleton className="h-3 w-16 rounded" />
          </CardHeader>
          <CardContent>
            <Skeleton className="h-8 w-2/3 rounded" />
          </CardContent>
        </Card>

        <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2">
          <CardSkeleton titleWidth="w-40" rows={4} />
          <CardSkeleton titleWidth="w-32" rows={4} />
        </div>
      </div>
    </div>
  );
}
