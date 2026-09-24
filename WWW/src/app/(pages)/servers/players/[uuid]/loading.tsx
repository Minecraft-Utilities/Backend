import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import Card, { CardContent } from "@/components/ui/card";
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemFooter,
  ItemMedia,
  ItemTitle,
} from "@/components/ui/item";
import { Separator } from "@/components/ui/separator";

function SightingRowSkeleton() {
  return (
    <Card className="h-fit w-full min-w-0 overflow-hidden p-0">
      <Item className="p-4">
        <ItemMedia variant="icon">
          <Skeleton className="size-4 rounded" />
        </ItemMedia>
        <ItemContent className="min-w-0">
          <ItemTitle>
            <Skeleton className="h-4 w-32 rounded" />
          </ItemTitle>
          <ItemDescription>
            <Skeleton className="h-3 w-44 rounded" />
          </ItemDescription>
        </ItemContent>
        <ItemActions>
          <Skeleton className="size-8 rounded-md" />
        </ItemActions>
        <ItemFooter className="flex-col items-stretch gap-4">
          <Separator />
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="flex flex-col gap-1.5">
                <Skeleton className="h-4 w-24 rounded" />
                <Skeleton className="h-3 w-16 rounded" />
              </div>
            ))}
          </div>
        </ItemFooter>
      </Item>
    </Card>
  );
}

export default function TrackedPlayerLoading() {
  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8" aria-live="polite" aria-busy="true">
      <TrackerPageHeader
        breadcrumbs={[{ label: "Player history", href: "/servers/players" }, { label: "Player" }]}
        title="Loading server history"
        description="Preparing public server sightings."
        active="players"
      />

      <div className="flex w-full max-w-5xl flex-col gap-4">
        <Card className="h-fit w-full min-w-0 overflow-hidden p-0">
          <CardContent className="grid gap-5 p-5 md:grid-cols-[minmax(0,1.4fr)_repeat(3,minmax(7rem,0.6fr))] md:items-center">
            <div className="min-w-0">
              <Skeleton className="h-3 w-20 rounded" />
              <Skeleton className="mt-2 h-7 w-64 max-w-full rounded-md" />
            </div>
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="flex flex-col gap-1.5">
                <Skeleton className="h-7 w-16 rounded" />
                <Skeleton className="h-3 w-24 rounded" />
              </div>
            ))}
          </CardContent>
        </Card>

        <ol className="border-border/60 ml-2 flex flex-col border-l pl-6" aria-hidden>
          {Array.from({ length: 3 }).map((_, index) => (
            <li key={index} className="relative pb-4 last:pb-0">
              <Skeleton className="ring-background absolute top-5 -left-[31px] size-2.5 rounded-full ring-4" />
              <SightingRowSkeleton />
            </li>
          ))}
        </ol>

        <PaginationSkeleton />
      </div>
    </div>
  );
}
