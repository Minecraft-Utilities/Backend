import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function PlayerSkinCardSkeleton() {
  return (
    <Card className="overflow-hidden p-0">
      <CardHeader>Skin</CardHeader>
      <CardContent className="relative flex flex-col items-center gap-4">
        <div className="flex h-72 w-full animate-pulse items-center justify-center overflow-hidden" />
        <Skeleton className="absolute top-2 right-2 size-9 rounded-md" />
        <div className="flex flex-wrap items-center justify-center gap-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="size-10 rounded-lg" />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
