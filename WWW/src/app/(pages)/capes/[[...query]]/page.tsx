import { mcUtilsApi } from "@/common/mc-utils";
import OwnerCount from "@/components/owner-count";
import SimpleLink from "@/components/simple-link";
import Card, { CardContent } from "@/components/ui/card";
import { Metadata } from "next";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Capes",
  description: "A list of all known capes in Minecraft",
};

export default async function CapesPage({ params }: PageProps<"/capes/[[...query]]">) {
  const { query } = await params;
  const page = Number(query?.[0] ?? "1");

  const capesResponse = await mcUtilsApi.fetchCapes(page);
  const capes = capesResponse.capes;
  const isEmpty = !capes || capes.items.length === 0;

  return (
    <div className="mt-10 flex w-full flex-col items-center justify-center gap-10">
      <header className="w-full max-w-[980px]">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">Minecraft Capes</h1>
        <p className="text-muted-foreground mt-2 text-sm">
          A list of all official Minecraft capes and which players own and have used them
        </p>
      </header>

      {isEmpty ? (
        <Card className="w-full max-w-md">
          <CardContent className="py-8 text-center">
            <p className="text-muted-foreground">No capes found.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="w-full max-w-[980px]">
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-5">
            {capes.items.map(cape => (
              <SimpleLink key={cape.id} href={`/cape/${cape.id}`} className="group block w-full">
                <Card className="overflow-hidden transition-transform duration-200 hover:-translate-y-0.5">
                  <CardContent className="relative flex min-h-0 flex-1 flex-col items-center justify-center p-0">
                    <div className="flex items-center justify-center px-4 pt-4 pb-15">
                      <img
                        src={`https://mc.fascinated.cc/api/skins/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b/fullbody_iso_back.png?capeId=${cape.id}`}
                        className="object-fit mx-auto h-46"
                        alt={`Cape ${cape.id}`}
                        sizes="(max-width: 640px) 50vw, 120px"
                      />
                    </div>
                    <div className="absolute inset-x-0 bottom-0 flex flex-col items-center gap-0.5 bg-linear-to-t from-black/70 to-transparent px-3 pt-8 pb-3">
                      <p className="w-full truncate text-center text-sm font-medium text-white">
                        {cape.name ?? "Unknown Cape"}
                      </p>
                      <OwnerCount count={cape.uniqueOwners} />
                    </div>
                  </CardContent>
                </Card>
              </SimpleLink>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
