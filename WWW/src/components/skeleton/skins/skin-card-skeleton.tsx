import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent } from "@/components/ui/card";
import { UserIcon } from "lucide-react";

export default function SkinCardSkeleton() {
  return (
    <Card className="w-full overflow-hidden transition-transform duration-200 hover:-translate-y-0.5">
      <CardContent className="relative flex min-h-0 flex-1 flex-col items-center justify-center p-0">
        <div className="flex items-center justify-center px-4 pt-4 pb-10">
          <Skeleton className="h-42 w-[120px]" />
        </div>
        <div className="absolute inset-x-0 bottom-0 flex flex-col items-center gap-0.5 bg-linear-to-t from-black/70 to-transparent px-3 pt-8 pb-3">
          <div className="flex items-center gap-1">
            <UserIcon className="size-3.5 text-white/60" />
            <Skeleton className="h-3 w-10" />
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
