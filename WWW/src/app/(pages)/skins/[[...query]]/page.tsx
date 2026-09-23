import { mcUtilsApi } from "@/common/mc-utils";
import OwnerCount from "@/components/owner-count";
import SimpleLink from "@/components/simple-link";
import { Button } from "@/components/ui/button";
import Card, { CardContent } from "@/components/ui/card";
import Pagination from "@/components/ui/pagination";
import { Award, Clock, TrendingUpIcon } from "lucide-react";
import { SkinLookupSort } from "mcutils-js-api/dist/types/player/skin/skin-lookup-sort";
import { Metadata } from "next";
import Link from "next/link";
import { ReactNode } from "react";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Skins",
  description: "The list of all seen skins for players",
};

export const SKIN_SORT_OPTIONS: Record<SkinLookupSort, { label: string; icon: ReactNode }> = {
  trending: { label: "Trending", icon: <TrendingUpIcon /> },
  top: { label: "Top", icon: <Award /> },
  latest: { label: "Latest", icon: <Clock /> },
};

export default async function SkinsPage({ params }: PageProps<"/skins/[[...query]]">) {
  const { query } = await params;

  const rawFirst = query?.[0];
  const isSort = rawFirst !== undefined && Object.keys(SKIN_SORT_OPTIONS).includes(rawFirst);
  const sort = (isSort ? rawFirst : "trending") as SkinLookupSort;
  const page = Number(isSort ? (query!.length >= 2 ? query![1] : "1") : (rawFirst ?? "1"));

  const skinsResponse = await mcUtilsApi.fetchSkins(page, sort);
  const skins = skinsResponse.skins;
  const isEmpty = skins?.items.length === 0 || !skins;

  return (
    <div className="mt-10 flex w-full flex-col items-center justify-center gap-10">
      <header className="w-full max-w-[980px]">
        <h1 className="text-foreground text-4xl font-bold tracking-tight">Minecraft Skins</h1>
        <p className="text-muted-foreground mt-2 text-sm">Discover thousands of Minecraft skins</p>
      </header>

      {isEmpty ? (
        <Card className="w-full max-w-md">
          <CardContent className="py-8 text-center">
            <p className="text-muted-foreground">No skins found.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="w-full max-w-[980px]">
          <div className="mb-6 flex flex-row justify-center gap-4 sm:justify-end">
            <div className="border-border bg-background/60 flex flex-wrap items-center justify-end gap-2 rounded-md border px-3 py-2 shadow-sm">
              {Object.entries(SKIN_SORT_OPTIONS).map(([key, label]) => (
                <Button
                  key={key}
                  asChild
                  variant={sort === key ? "default" : "secondary"}
                  aria-current={sort === key ? "page" : undefined}
                >
                  <Link href={`/skins/${key}`}>
                    {label.icon}
                    {label.label}
                  </Link>
                </Button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-5">
            {skins.items.map(skin => (
              <SimpleLink key={skin.id} href={`/skin/${skin.id}`} className="group block w-full">
                <Card className="overflow-hidden transition-transform duration-200 hover:-translate-y-0.5">
                  <CardContent className="relative flex min-h-0 flex-1 flex-col items-center justify-center p-0">
                    <div className="flex items-center justify-center px-4 pt-4 pb-10">
                      <img
                        src={skin.parts.FULLBODY_ISO_FRONT}
                        className="object-fit mx-auto h-42"
                        alt={`Skin ${skin.id}`}
                        sizes="(max-width: 640px) 50vw, 120px"
                      />
                    </div>
                    <div className="absolute inset-x-0 bottom-0 flex flex-col items-center gap-0.5 bg-linear-to-t from-black/70 to-transparent px-3 pt-8 pb-3">
                      <OwnerCount count={skin.uniqueOwners} />
                    </div>
                  </CardContent>
                </Card>
              </SimpleLink>
            ))}
          </div>

          <div className="mt-6 flex justify-center">
            <Pagination
              page={page}
              totalItems={skins.totalItems ?? 0}
              itemsPerPage={skins.itemsPerPage ?? 0}
              basePath={`/skins/${sort}`}
            />
          </div>
        </div>
      )}
    </div>
  );
}
