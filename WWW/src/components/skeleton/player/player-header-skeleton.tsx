import Skeleton from "@/components/skeleton/ui/skeleton";

export default function PlayerHeaderSkeleton() {
  return (
    <header className="w-full text-left">
      <div className="flex items-center gap-3 sm:gap-4">
        <Skeleton className="size-14 shrink-0 rounded-md" />
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <Skeleton className="h-9 w-48 rounded-md sm:h-10 sm:w-64" />
          <Skeleton className="h-4 w-56 rounded-md sm:w-72" />
        </div>
      </div>
    </header>
  );
}
