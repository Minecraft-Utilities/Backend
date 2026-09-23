import Skeleton from "@/components/skeleton/ui/skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

function ProfileFieldSkeleton({ labelWidth, valueWidth }: { labelWidth: string; valueWidth: string }) {
  return (
    <div className="col-span-2 grid grid-cols-subgrid items-center py-2">
      <Skeleton className={`h-4 shrink-0 rounded ${labelWidth}`} />
      <Skeleton className={`h-4 rounded ${valueWidth}`} />
    </div>
  );
}

export default function ServerDetailsSkeleton() {
  return (
    <section className="flex flex-col gap-4">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardHeader>Details</CardHeader>
        <CardContent className="p-3 pt-2">
          <div className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-6 px-1">
            <ProfileFieldSkeleton labelWidth="w-14" valueWidth="w-20" />
            <ProfileFieldSkeleton labelWidth="w-20" valueWidth="w-full max-w-[200px]" />
            <ProfileFieldSkeleton labelWidth="w-16" valueWidth="w-28" />
            <ProfileFieldSkeleton labelWidth="w-10" valueWidth="w-12" />
            <ProfileFieldSkeleton labelWidth="w-28" valueWidth="w-36" />
            <ProfileFieldSkeleton labelWidth="w-24" valueWidth="w-10" />
            <ProfileFieldSkeleton labelWidth="w-28" valueWidth="w-10" />
            <ProfileFieldSkeleton labelWidth="w-32" valueWidth="w-10" />
            <ProfileFieldSkeleton labelWidth="w-24" valueWidth="w-10" />
            <ProfileFieldSkeleton labelWidth="w-20" valueWidth="w-10" />
            <ProfileFieldSkeleton labelWidth="w-16" valueWidth="w-10" />
          </div>
        </CardContent>
      </Card>
    </section>
  );
}
