import Skeleton from "./skeleton";

export interface DetailRowsSkeletonProps {
  count: number;
  /** Label placeholder width: "short" (w-12) for player details, "default" (w-16) for server details */
  labelWidth?: "short" | "default";
}

export default function DetailRowsSkeleton({ count, labelWidth = "default" }: DetailRowsSkeletonProps) {
  const labelClass = labelWidth === "short" ? "w-12" : "w-16";
  return (
    <div className="flex flex-col">
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="xs:flex-row xs:flex-wrap xs:items-center xs:justify-between xs:gap-2 flex flex-col gap-1.5 py-1.5 first:pt-0 last:pb-0"
        >
          <Skeleton className={`h-4 ${labelClass} shrink-0 rounded`} />
          <Skeleton className="h-7 min-w-0 flex-1 rounded-md px-2 py-1" />
        </div>
      ))}
    </div>
  );
}
