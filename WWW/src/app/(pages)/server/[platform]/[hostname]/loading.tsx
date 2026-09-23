import ServerDetailsSkeleton from "@/components/skeleton/server/server-details-skeleton";
import ServerDnsRecordsSkeleton from "@/components/skeleton/server/server-dns-records-skeleton";
import ServerMotdSkeleton from "@/components/skeleton/server/server-motd-skeleton";

export default function ServerLoading() {
  return (
    <div className="mt-16 flex w-full flex-col items-center gap-6" aria-live="polite" aria-busy="true">
      <div className="flex w-full max-w-3xl flex-col gap-16">
        <header className="flex min-w-0 flex-1 flex-col items-center gap-4">
          <div className="flex flex-col items-center gap-4 md:flex-row">
            <div className="bg-muted/30 size-16 animate-pulse rounded-md" />
            <h1 className="text-foreground min-w-0 text-center text-2xl font-bold tracking-tight wrap-break-word md:text-4xl">
              Loading...
            </h1>
          </div>
        </header>

        <div className="flex flex-col gap-4">
          <ServerMotdSkeleton />
          <ServerDetailsSkeleton />
          <ServerDnsRecordsSkeleton />
        </div>
      </div>
    </div>
  );
}
