import TableSkeleton from "@/components/skeleton/ui/table-skeleton";
import Card, { CardContent, CardHeader } from "@/components/ui/card";

export default function ServerDnsRecordsSkeleton() {
  return (
    <section className="flex flex-col gap-4">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardHeader>DNS Records</CardHeader>
        <CardContent>
          <div className="flex flex-col gap-4">
            <TableSkeleton columns={3} rows={4} />
          </div>
        </CardContent>
      </Card>
    </section>
  );
}
