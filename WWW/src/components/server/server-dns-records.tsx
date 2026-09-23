import { DnsRecord, SRVRecord } from "mcutils-js-api/dist/types/dns/dns-record";
import Card, { CardContent, CardHeader } from "../ui/card";

function isSrvRecord(r: DnsRecord): r is SRVRecord {
  return r.type === "SRV";
}

export interface ServerDnsRecordsProps {
  records: DnsRecord[];
}

export default function ServerDnsRecords({ records }: ServerDnsRecordsProps) {
  return (
    records &&
    records.length > 0 && (
      <section className="flex flex-col gap-4">
        <Card className="w-full min-w-0 overflow-hidden p-0">
          <CardHeader>DNS Records</CardHeader>
          <CardContent>
            <div className="flex flex-col gap-4">
              <div className="border-border min-w-0 overflow-x-auto rounded-lg border [-webkit-overflow-scrolling:touch]">
                <table className="w-full min-w-[320px] text-left text-sm">
                  <thead>
                    <tr className="border-border bg-muted/30 border-b">
                      <th className="text-muted-foreground px-3 py-2 font-medium whitespace-nowrap">
                        Hostname
                      </th>
                      <th className="text-muted-foreground px-3 py-2 font-medium whitespace-nowrap">Type</th>
                      <th className="text-muted-foreground px-3 py-2 font-medium whitespace-nowrap">Data</th>
                    </tr>
                  </thead>
                  <tbody>
                    {records.map(record => {
                      const rowKey = isSrvRecord(record)
                        ? `srv-${record.name}-${record.port}-${record.target}`
                        : `record-${record.name}-${record.type}-${record.address ?? ""}`;
                      return (
                        <tr key={rowKey} className="border-border/50 border-b last:border-0">
                          <td className="text-foreground px-3 py-2 font-mono whitespace-nowrap">
                            {isSrvRecord(record) ? record.name : (record.name ?? "Unknown")}
                          </td>
                          <td className="text-foreground px-3 py-2 font-mono whitespace-nowrap">
                            {record.type}
                          </td>
                          <td className="text-muted-foreground px-3 py-2 font-mono whitespace-nowrap">
                            {isSrvRecord(record)
                              ? `${record.priority} ${record.weight} ${record.port} ${record.target}`
                              : (record.address ?? "Unknown")}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </CardContent>
        </Card>
      </section>
    )
  );
}
