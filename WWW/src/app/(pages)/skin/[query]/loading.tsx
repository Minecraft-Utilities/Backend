import SkinHeadCommandsSkeleton from "@/components/skeleton/skin/skin-head-commands-skeleton";
import SkinHeaderSkeleton from "@/components/skeleton/skin/skin-header-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function SkinLoading() {
  return (
    <div
      className="mt-10 flex w-full flex-col items-center justify-center gap-10"
      aria-live="polite"
      aria-busy="true"
    >
      <SkinHeaderSkeleton />

      <div className="flex w-full max-w-[980px] flex-col gap-4 lg:flex-row">
        {/* Left: preview + details */}
        <div className="flex w-full min-w-0 flex-1 flex-col gap-4">
          <Card className="h-fit w-full">
            <CardContent className="flex items-center justify-center">
              <div className="bg-muted h-96 w-full rounded-xl" />
            </CardContent>
          </Card>

          <Card className="h-fit w-full overflow-hidden p-0">
            <CardHeader>Details</CardHeader>
            <CardContent className="flex flex-col gap-3">
              <Skeleton className="h-5 w-full rounded" />
              <Skeleton className="h-5 w-2/3 rounded" />
              <Skeleton className="h-5 w-3/4 rounded" />
            </CardContent>
          </Card>
        </div>

        {/* Right: players + commands */}
        <div className="flex w-full min-w-0 flex-col gap-4 lg:max-w-xl">
          <Card className="h-fit w-full text-sm">
            <CardHeader>
              <p>Players</p>
            </CardHeader>
            <CardContent className="flex h-64 flex-wrap gap-2">
              {Array.from({ length: 12 }).map((_, i) => (
                <Skeleton key={i} className="h-5 w-20 rounded" />
              ))}
            </CardContent>
          </Card>

          <SkinHeadCommandsSkeleton />
        </div>
      </div>
    </div>
  );
}
