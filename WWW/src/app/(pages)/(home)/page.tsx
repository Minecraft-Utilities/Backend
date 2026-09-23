import { env } from "@/common/env";
import { mcUtilsApi } from "@/common/mc-utils";
import LandingQuerySearch from "@/components/landing/landing-query-search";
import Statistics from "@/components/landing/statistics";
import OwnerCount from "@/components/owner-count";
import { Button } from "@/components/ui/button";
import Card from "@/components/ui/card";
import { ArrowRightIcon } from "lucide-react";
import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import Script from "next/script";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "MC Utils: Minecraft Player & Server Lookup",
  description:
    "Look up any Minecraft player by username or UUID: view their skin, cape, and profile history. Check Java and Bedrock server status, player counts, MOTD, and more.",
  openGraph: {
    url: env.NEXT_PUBLIC_BASE_URL,
    title: "MC Utils: Minecraft Player & Server Lookup",
    description:
      "Look up any Minecraft player by username or UUID: view their skin, cape, and profile history. Check Java and Bedrock server status, player counts, MOTD, and more.",
  },
};

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "MC Utils",
  url: env.NEXT_PUBLIC_BASE_URL,
  applicationCategory: "UtilitiesApplication",
  operatingSystem: "All",
  description:
    "Look up any Minecraft player by username or UUID: view their skin, cape, and profile history. Check Java and Bedrock server status, player counts, MOTD, and more.",
  offers: {
    "@type": "Offer",
    price: "0",
    priceCurrency: "USD",
  },
};

export default async function HomePage() {
  const { skins: trendingSkins } = await mcUtilsApi.fetchSkins(1, "trending");

  return (
    <>
      <Script
        id="homepage-jsonld"
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <div className="mx-auto mt-16 flex w-full max-w-[980px] flex-col items-center gap-16">
        <header className="flex flex-col items-center gap-4 text-center">
          <h1 className="text-foreground text-3xl font-semibold tracking-tight sm:text-4xl">
            Minecraft Player & Server Lookup
          </h1>
          <p className="text-muted-foreground text-sm sm:text-base">
            Look up any player by Username / UUID or inspect any server by hostname.
          </p>
        </header>

        <div className="flex w-full flex-col items-center gap-20">
          <LandingQuerySearch />

          {trendingSkins && trendingSkins.items.length > 0 && (
            <div className="flex w-full flex-col gap-4">
              <div className="flex items-center justify-between">
                <h2 className="text-foreground text-xl font-bold tracking-tight">Trending Skins</h2>
                <Button variant="secondary" className="hover:bg-secondary">
                  <Link href="/skins/trending" className="flex items-center gap-1">
                    View All
                    <ArrowRightIcon className="size-4" />
                  </Link>
                </Button>
              </div>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-6">
                {trendingSkins.items.slice(0, 12).map(skin => (
                  <Card key={skin.id} className="transition-transform duration-200 hover:-translate-y-0.5">
                    <Link
                      href={`/skin/${skin.id}`}
                      className="flex flex-col items-center justify-center gap-2 p-2"
                    >
                      <Image
                        src={`${skin.parts.FULLBODY_ISO_FRONT}`}
                        alt={`Trending skin ${skin.id}`}
                        width={128}
                        height={128}
                        className="max-h-full max-w-full object-contain p-1"
                      />
                      <OwnerCount count={skin.uniqueOwners} />
                    </Link>
                  </Card>
                ))}
              </div>
            </div>
          )}

          <Statistics />
        </div>

        <section className="text-muted-foreground max-w-2xl text-center text-sm leading-relaxed">
          <p>
            MC Utils is a free Minecraft lookup tool. Search any player to view their current skin, cape,
            UUID, and name history. Paste a Java or Bedrock server address to instantly check whether
            it&apos;s online, see its MOTD, and monitor live player counts: no login required.
          </p>
        </section>
      </div>
    </>
  );
}
