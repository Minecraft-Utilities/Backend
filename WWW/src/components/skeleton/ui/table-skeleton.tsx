import Skeleton from "./skeleton";

export interface TableSkeletonProps {
  columns?: number;
  rows?: number;
}

const defaultHeaderWidths = ["w-20", "w-12", "w-14"];
const defaultCellWidths = ["w-24", "w-10", "w-32"];

export default function TableSkeleton({ columns = 3, rows = 4 }: TableSkeletonProps) {
  const headerWidths = defaultHeaderWidths.slice(0, columns);
  const cellWidths = defaultCellWidths.slice(0, columns);
  return (
    <div className="border-border min-w-0 overflow-x-auto rounded-lg border [-webkit-overflow-scrolling:touch]">
      <table className="w-full min-w-[320px] text-left text-sm">
        <thead>
          <tr className="border-border bg-muted/30 border-b">
            {headerWidths.map((w, i) => (
              <th
                key={`header-${i}-${w}`}
                className="text-muted-foreground px-3 py-2 font-medium whitespace-nowrap"
              >
                <Skeleton className={`h-4 ${w} rounded`} />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }).map((_, rowIndex) => (
            <tr key={`row-${rowIndex}`} className="border-border/50 border-b last:border-0">
              {cellWidths.map((w, colIndex) => (
                <td key={`cell-${rowIndex}-${colIndex}-${w}`} className="px-3 py-2">
                  <Skeleton className={`h-4 ${w} rounded font-mono`} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
