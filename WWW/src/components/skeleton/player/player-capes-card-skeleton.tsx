import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function PlayerCapesCardSkeleton() {
  return (
    <Card className="h-fit min-w-48 overflow-hidden p-0">
      <CardHeader>Capes</CardHeader>
      <CardContent className="flex h-full items-center justify-center gap-2">
        <Skeleton className="h-[64px] w-10 rounded-sm" />
      </CardContent>
    </Card>
  );
}
