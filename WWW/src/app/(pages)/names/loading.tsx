import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function NamesLoading() {
  return (
    <div
      className="mt-10 flex w-full flex-col items-center justify-center gap-10"
      aria-live="polite"
      aria-busy="true"
    >
      <header className="w-full max-w-[980px]">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">Recent Name Changes</h1>
        <p className="text-muted-foreground mt-2 text-sm">
          Recent Minecraft username changes across all players tracked by MC Utils.
        </p>
      </header>

      <Card className="w-full max-w-[980px] overflow-hidden p-0">
        <CardHeader>Name Changes</CardHeader>
        <CardContent className="p-0">
          <div className="divide-border/60 divide-y">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="flex items-center gap-2 px-3 py-2">
                <Skeleton className="h-6 w-6 rounded-sm" />

                <div className="flex min-w-0 flex-1 items-center gap-1.5 text-sm">
                  <Skeleton className="h-4 w-24 rounded" />
                  <Skeleton className="h-3 w-3 rounded-full" />
                  <Skeleton className="h-4 min-w-0 flex-1 rounded" />
                </div>

                <Skeleton className="h-3 w-14 rounded" />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
