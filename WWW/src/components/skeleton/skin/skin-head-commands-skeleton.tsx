import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function SkinHeadCommandsSkeleton() {
  return (
    <Card>
      <CardHeader>Head Commands (for Command Blocks)</CardHeader>
      <CardContent className="flex flex-row gap-0">
        <Skeleton className="h-9 w-[105px] shrink-0 rounded-r-none" />
        <Skeleton className="min-w-0 flex-1 rounded-none" />
        <Skeleton className="size-9 shrink-0 rounded-l-none" />
      </CardContent>
    </Card>
  );
}
