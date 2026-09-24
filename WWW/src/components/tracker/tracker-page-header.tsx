import { cn } from "@/common/utils";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import Link from "next/link";
import { Fragment, type ReactNode } from "react";

export type TrackerPageSection = "overview" | "browse" | "players";

export interface TrackerBreadcrumb {
  label: string;
  /** Omit on the final crumb; it renders as the current page. */
  href?: string;
}

export interface TrackerPageHeaderProps {
  eyebrow?: string;
  /** Ancestor trail for detail pages; section pages omit it and rely on the nav. */
  breadcrumbs?: TrackerBreadcrumb[];
  title: string;
  description: string;
  active: TrackerPageSection;
  actions?: ReactNode;
}

const navigation = [
  { id: "overview", label: "Overview", href: "/servers" },
  { id: "browse", label: "Browse servers", href: "/servers/browse" },
  { id: "players", label: "Player history", href: "/servers/players" },
] as const;

export default function TrackerPageHeader({
  eyebrow,
  breadcrumbs,
  title,
  description,
  active,
  actions,
}: TrackerPageHeaderProps) {
  return (
    <header className="relative isolate flex w-full max-w-5xl flex-col gap-5">
      {/* The page background image sits behind the header, and its brightest areas
          wash out muted text; a soft vignette restores contrast without boxing the header in.
          It stays within the header's own box so narrow viewports gain no horizontal scroll. */}
      <div
        className="pointer-events-none absolute inset-x-0 -inset-y-8 -z-10 bg-[radial-gradient(ellipse_at_top,rgb(0_0_0_/_0.55),transparent_72%)]"
        aria-hidden
      />
      {breadcrumbs && breadcrumbs.length > 0 ? (
        <Breadcrumb>
          <BreadcrumbList>
            {breadcrumbs.map((crumb, index) => (
              <Fragment key={crumb.label}>
                {index > 0 ? <BreadcrumbSeparator /> : null}
                <BreadcrumbItem>
                  {crumb.href && index < breadcrumbs.length - 1 ? (
                    <BreadcrumbLink asChild>
                      <Link href={crumb.href}>{crumb.label}</Link>
                    </BreadcrumbLink>
                  ) : (
                    <BreadcrumbPage>{crumb.label}</BreadcrumbPage>
                  )}
                </BreadcrumbItem>
              </Fragment>
            ))}
          </BreadcrumbList>
        </Breadcrumb>
      ) : null}

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
