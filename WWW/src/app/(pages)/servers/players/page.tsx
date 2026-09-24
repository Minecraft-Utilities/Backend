import TrackerPageHeader from "@/components/tracker/tracker-page-header";
import TrackerPlayerSearch from "@/components/tracker/tracker-player-search";
import Card, { CardContent } from "@/components/ui/card";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Player Server History",
  description: "Search Minecraft usernames and open their public server history on MC Utils.",
};

export default function TrackedPlayerLookupPage() {
  return (
    <div className="mt-10 flex w-full flex-col items-center gap-8">
      <TrackerPageHeader
        title="Player server history"
        description="Search by username to see the public servers where a verified player was observed."
        active="players"
      />

      <Card className="w-full max-w-3xl overflow-hidden p-0">
        <CardContent className="flex flex-col gap-4 p-5 sm:p-7">
          <div>
            <h2 className="text-foreground text-xl font-semibold tracking-tight">Find a player</h2>
            <p className="text-muted-foreground mt-1 text-sm">
              Autocomplete uses the same player search as the main MC Utils lookup.
            </p>
          </div>
          <TrackerPlayerSearch />
          <p className="text-muted-foreground text-xs">
            Server history contains only identity-verified public status samples.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
