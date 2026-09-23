import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function PlayerUsernameHistorySkeleton() {
  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Name History</CardHeader>
      <CardContent className="p-0">
        <div className="divide-border/60 divide-y">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex items-center gap-2 px-3 py-1.5">
              <Skeleton className="h-4 w-20 rounded" />
              <Skeleton className="h-3 w-3 rounded-full" />
              <Skeleton className="h-4 min-w-0 flex-1 rounded" />
              <Skeleton className="h-3 w-10 rounded" />
              <Skeleton className="h-4 w-9 shrink-0 rounded" />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
