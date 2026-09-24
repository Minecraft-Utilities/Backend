import PaginationSkeleton from "@/components/skeleton/ui/pagination-skeleton";
import Skeleton from "@/components/skeleton/ui/skeleton";
import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

function FieldSkeleton() {
  return (
    <div className="flex flex-col gap-1.5">
      <Skeleton className="h-4 w-20 rounded" />
      <Skeleton className="h-9 w-full rounded-md" />
    </div>
  );
}

function ServerRowSkeleton() {
  return (
    <TableRow>
      <TableCell className="w-full max-w-0 px-4 py-3">
        <div className="flex items-center gap-2.5">
          <Skeleton className="size-2 shrink-0 rounded-full" />
          <Skeleton className="h-4 w-40 rounded" />
        </div>
        <Skeleton className="mt-1.5 ml-[18px] h-3 w-24 rounded" />
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
      <TableCell className="hidden w-10 px-2 py-3 md:table-cell" />
    </TableRow>
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

      <div className="flex w-full max-w-5xl flex-col gap-4">
        <Card className="overflow-hidden p-0">
          <CardHeader>
            <Skeleton className="h-3 w-28 rounded" />
          </CardHeader>
          <CardContent className="flex flex-col gap-4 p-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <FieldSkeleton />
              <FieldSkeleton />
            </div>
            <Skeleton className="h-8 w-32 rounded-md" />
          </CardContent>
        </Card>

        <Card className="overflow-hidden p-0">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="h-11 px-4">
                  <Skeleton className="h-3 w-16 rounded" />
                </TableHead>
                <TableHead className="hidden px-4 md:table-cell">
                  <Skeleton className="h-3 w-14 rounded" />
                </TableHead>
                <TableHead className="hidden px-4 md:table-cell">
                  <Skeleton className="h-3 w-16 rounded" />
                </TableHead>
                <TableHead className="hidden px-4 md:table-cell">
                  <Skeleton className="ml-auto h-3 w-14 rounded" />
                </TableHead>
                <TableHead className="hidden w-10 px-2 md:table-cell" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {Array.from({ length: 10 }).map((_, index) => (
                <ServerRowSkeleton key={index} />
              ))}
            </TableBody>
          </Table>
        </Card>

        <PaginationSkeleton />
      </div>
    </div>
  );
}
