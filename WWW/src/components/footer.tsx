"use client";

import { cn } from "@/common/utils";
import SimpleLink from "@/components/simple-link";
import { ExternalLink } from "lucide-react";
import Image from "next/image";

type FooterLink = {
  name: string;
  href: string;
};

const links: { [category: string]: FooterLink[] } = {
  Resources: [
    {
      name: "Source Code",
      href: "https://github.com/Minecraft-Utilities",
    },
    {
      name: "Documentation",
      href: "https://mc.fascinated.cc/api/swagger-ui/index.html",
    },
    {
      name: "Server Registry",
      href: "https://github.com/Minecraft-Utilities/ServerRegistry",
    },
  ],
};

export default function Footer() {
  return (
    <footer
      className={cn(
        "border-border bg-secondary/60 mt-5 flex min-h-40 shrink-0 flex-col justify-between gap-10 border-t px-10 py-5 select-none lg:gap-0"
      )}
    >
      <div className="flex justify-center">
        <div className="flex w-full max-w-(--breakpoint-2xl) flex-col items-center justify-between gap-7 lg:flex-row lg:items-start">
          {/* Branding & social */}
          <div className="flex flex-col gap-5">
            <div className="flex flex-col items-center gap-2 text-center lg:items-start lg:text-left">
              <SimpleLink
                className="flex items-center gap-3 transition-all hover:opacity-75"
                href="/"
                draggable={false}
              >
                <Image src="/media/logo/logo.png" alt="MC Utils" width={36} height={36} className="size-9" />
                <span className="text-primary text-xl font-bold">MC Utils</span>
              </SimpleLink>
              <p className="max-w-md text-sm opacity-85">
                API for Minecraft player data (skins, capes, profiles), Java/Bedrock server status and
                previews.
              </p>
            </div>
          </div>

          {/* Link columns */}
          <div className="flex gap-20 transition-all md:gap-32">
            {Object.entries(links).map(([title, linkList]) => (
              <div key={title} className="flex flex-col gap-0.5">
                <h2 className="text-primary pb-0.5 text-lg font-semibold">{title}</h2>
                {linkList.map(link => {
                  const external = !link.href.startsWith("/");
                  return (
                    <SimpleLink
                      key={link.name}
                      className="flex items-center gap-2 text-sm transition-all hover:opacity-75"
                      href={link.href}
                      target={external ? "_blank" : undefined}
                      rel={external ? "noopener noreferrer" : undefined}
                      draggable={false}
                    >
                      <span>{link.name}</span>
                      {external && <ExternalLink className="size-3.5" />}
                    </SimpleLink>
                  );
                })}
              </div>
            ))}
          </div>
        </div>
      </div>
    </footer>
  );
}
