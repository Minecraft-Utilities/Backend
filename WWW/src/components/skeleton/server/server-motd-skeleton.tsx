import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent } from "@/components/ui/card";

export default function ServerMotdSkeleton() {
  return (
    <section className="flex flex-col gap-4">
      <Card className="h-fit items-center overflow-hidden p-0">
        <CardContent className="flex items-center justify-center">
          <Skeleton className="h-[87px] w-auto max-w-2xl rounded-md" />
        </CardContent>
      </Card>
    </section>
  );
}
