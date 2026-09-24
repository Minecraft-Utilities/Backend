import Skeleton from "@/components/skeleton/ui/skeleton";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { Item, ItemActions, ItemContent, ItemGroup, ItemMedia } from "@/components/ui/item";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

function StatStripSkeleton() {
  return (
    <Card className="w-full">
      <CardContent>
        <ItemGroup className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <Item key={index} variant="muted" size="sm">
              <ItemContent>
                <Skeleton className="h-4 w-24 rounded" />
                <Skeleton className="h-8 w-28 rounded" />
                <Skeleton className="h-3 w-32 rounded" />
              </ItemContent>
            </Item>
          ))}
        </ItemGroup>
      </CardContent>
    </Card>
  );
}

function PieCardSkeleton() {
  return (
    <Card className="w-full">
      <CardHeader>
        <div className="flex w-full items-center justify-between gap-2">
          <Skeleton className="h-3 w-24 rounded" />
          <Skeleton className="h-5 w-12 rounded-full" />
        </div>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-6">
        <Skeleton className="size-48 shrink-0 rounded-full" />
        <ItemGroup className="min-w-0">
          {Array.from({ length: 5 }).map((_, index) => (
            <Item key={index} size="xs">
              <ItemMedia>
                <Skeleton className="size-2.5 shrink-0 rounded-full" />
              </ItemMedia>
              <ItemContent className="min-w-0">
                <Skeleton className="h-4 w-32 max-w-full rounded" />
              </ItemContent>
              <ItemActions>
                <Skeleton className="h-4 w-10 rounded" />
                <Skeleton className="h-4 w-12 rounded" />
              </ItemActions>
            </Item>
          ))}
        </ItemGroup>
      </CardContent>
    </Card>
  );
}

function RecentServerRowSkeleton() {
  return (
    <TableRow>
      <TableCell className="w-full max-w-0 px-4 py-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <Skeleton className="size-2 shrink-0 rounded-full" />
          <Skeleton className="h-4 w-2/3 rounded" />
        </div>
        <div className="mt-0.5 pl-[18px]">
          <Skeleton className="h-3 w-1/2 rounded" />
        </div>
      </TableCell>
      <TableCell className="hidden px-4 py-3 md:table-cell">
        <Skeleton className="h-4 w-16 rounded" />
      </TableCell>
      <TableCell className="hidden px-4 py-3 md:table-cell">
        <Skeleton className="h-4 w-24 rounded" />
      </TableCell>
      <TableCell className="hidden px-4 py-3 md:table-cell">
        <Skeleton className="ml-auto h-4 w-16 rounded" />
      </TableCell>
      <TableCell className="hidden w-10 px-2 py-3 md:table-cell">
        <Skeleton className="size-4 rounded" />
      </TableCell>
    </TableRow>
  );
}

function RecentServersSkeleton() {
  return (
    <Card className="overflow-hidden p-0">
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead className="text-muted-foreground h-11 px-4">Server</TableHead>
            <TableHead className="text-muted-foreground hidden px-4 md:table-cell">Players</TableHead>
            <TableHead className="text-muted-foreground hidden px-4 md:table-cell">Location</TableHead>
            <TableHead className="text-muted-foreground hidden px-4 text-right md:table-cell">
              Updated
            </TableHead>
            <TableHead className="hidden w-10 px-2 md:table-cell">
              <span className="sr-only">Open server</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {Array.from({ length: 6 }).map((_, index) => (
            <RecentServerRowSkeleton key={index} />
          ))}
        </TableBody>
      </Table>
    </Card>
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

      <div className="flex w-full max-w-5xl flex-col gap-4">
        <StatStripSkeleton />

        <div className="grid w-full grid-cols-1 gap-4 lg:grid-cols-2">
          <PieCardSkeleton />
          <PieCardSkeleton />
        </div>
      </div>

      <section className="flex w-full max-w-5xl flex-col gap-4">
        <div>
          <Skeleton className="h-7 w-44 rounded" />
          <Skeleton className="mt-2 h-3 w-72 max-w-full rounded" />
        </div>
        <RecentServersSkeleton />
      </section>
    </div>
  );
}
