"use client";

import { cn } from "@/common/utils";
import { AtSignIcon, ScanFaceIcon, ServerIcon, ShirtIcon } from "lucide-react";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { type ReactNode } from "react";
import SimpleLink from "../simple-link";
import QuerySearchNavbar from "./query-search-navbar";

interface NavLinkProps {
  name: string;
  icon: ReactNode;
  href: string;
  className?: string;
}

const links = [
  <NavLink key="capes" name="Capes" icon={<ShirtIcon className="size-5" />} href="/capes" />,
  <NavLink key="skins" name="Skins" icon={<ScanFaceIcon className="size-5" />} href="/skins" />,
  <NavLink key="names" name="Names" icon={<AtSignIcon className="size-5" />} href="/names" />,
  <NavLink key="servers" name="Servers" icon={<ServerIcon className="size-5" />} href="/servers" />,
];

export default function Navbar() {
  const path = usePathname();

  return (
    <nav className="border-border bg-background/55 sticky inset-x-0 top-0 z-50 flex h-(--nav-height) w-full items-center justify-between border-b px-2 py-1 backdrop-blur-md select-none lg:justify-around lg:px-8">
      <div className="flex w-full max-w-6xl justify-between">
        <div className="flex items-center gap-(--spacing-md) md:gap-6">
          {/* Branding */}
          <SimpleLink
            className="flex items-center gap-(--spacing-sm) hover:opacity-80 md:gap-2.5"
            href="/"
            draggable={false}
          >
            <Image width={28} height={28} className="size-7" src="/media/logo/logo.png" alt="MC Utils Logo" />

            <span className="text-primary hidden text-base font-bold sm:inline sm:text-lg" aria-hidden="true">
              MC Utils
            </span>
          </SimpleLink>

          {/* Links */}
          <div className="flex items-center gap-0.5 sm:gap-1">{links.map(link => link)}</div>
        </div>

        {path !== "/" && <QuerySearchNavbar />}
      </div>
    </nav>
  );
}

function NavLink({ name, icon, href, className }: NavLinkProps) {
  const pathname = usePathname();
  const isActive = pathname && (pathname === href || (href !== "/" && pathname.startsWith(href)));

  return (
    <SimpleLink
      className={cn(
        "group relative flex h-9 items-center gap-1.5 rounded-md border px-2 py-1.5 text-sm font-medium transition-colors duration-150",
        isActive
          ? "bg-primary/10 text-primary border-primary/20"
          : "text-muted-foreground hover:text-primary hover:bg-primary/5 border-transparent",
        className
      )}
      href={href}
      target={href.startsWith("/") ? "_self" : "_blank"}
      draggable={false}
    >
      {icon}
      <span className="hidden sm:flex">{name}</span>

      {/* Active indicator */}
      {isActive && (
        <div className="bg-primary absolute -bottom-0.5 left-1/2 h-0.5 w-4 -translate-x-1/2 rounded-full transition-opacity duration-200" />
      )}
    </SimpleLink>
  );
}
