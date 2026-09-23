import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function PlayerSkinsCardSkeleton() {
  return (
    <Card className="h-fit min-w-48 overflow-hidden p-0">
      <CardHeader>Skins</CardHeader>
      <CardContent className="flex h-full items-center justify-center gap-2">
        <Skeleton className="size-[38px] rounded-sm" />
        <Skeleton className="size-[38px] rounded-sm" />
        <Skeleton className="size-[38px] rounded-sm" />
      </CardContent>
    </Card>
  );
}
