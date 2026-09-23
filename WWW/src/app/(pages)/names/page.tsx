import { mcUtilsApi } from "@/common/mc-utils";
import NameChanges from "@/components/names/name-changes";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { Metadata } from "next";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Recent Name Changes",
  description: "Recent Minecraft username changes across all players tracked by MC Utils.",
};

export default async function NamesPage() {
  const { nameChanges, error } = await mcUtilsApi.getRecentNameChanges();

  return (
    <div className="mt-10 flex w-full flex-col items-center justify-center gap-10">
      <header className="w-full max-w-[980px]">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">Recent Name Changes</h1>
        <p className="text-muted-foreground mt-2 text-sm">
          Recent Minecraft username changes across all players tracked by MC Utils.
        </p>
      </header>

      {error && (
        <Card className="border-destructive/50 bg-destructive/10 w-full max-w-2xl overflow-hidden p-0">
          <CardHeader variant="destructive">Error</CardHeader>
          <CardContent>
            <p className="text-muted-foreground text-sm">{error.message}</p>
          </CardContent>
        </Card>
      )}

      {nameChanges && nameChanges.length > 0 && <NameChanges nameChanges={nameChanges} />}
    </div>
  );
}
