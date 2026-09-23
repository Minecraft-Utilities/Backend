import { cn } from "@/common/utils";
import Link from "next/link";
import type { ReactNode } from "react";

export type TrackerPageSection = "overview" | "browse";

export interface TrackerPageHeaderProps {
  eyebrow?: string;
  title: string;
  description: string;
  active: TrackerPageSection;
  actions?: ReactNode;
}

const navigation = [
  { id: "overview", label: "Overview", href: "/servers" },
  { id: "browse", label: "Browse servers", href: "/servers/browse" },
] as const;

export default function TrackerPageHeader({
  eyebrow,
  title,
  description,
  active,
  actions,
}: TrackerPageHeaderProps) {
  return (
    <header className="flex w-full max-w-5xl flex-col gap-5">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          {eyebrow ? <p className="text-primary mb-2 text-sm font-medium">{eyebrow}</p> : null}
          <h1 className="text-foreground text-3xl font-bold tracking-tight text-balance sm:text-4xl">
            {title}
          </h1>
          <p className="text-muted-foreground mt-2 max-w-2xl text-sm leading-relaxed text-pretty">
            {description}
          </p>
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
      </div>

      <nav className="border-border/70 flex gap-5 border-b" aria-label="Server tracker sections">
        {navigation.map(item => {
          const selected = active === item.id;
          return (
            <Link
              key={item.id}
              href={item.href}
              aria-current={selected ? "page" : undefined}
              className={cn(
                "-mb-px border-b-2 pb-3 text-sm font-medium transition-colors",
                selected
                  ? "border-primary text-foreground"
                  : "text-muted-foreground hover:text-foreground border-transparent"
              )}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
    </header>
  );
}
