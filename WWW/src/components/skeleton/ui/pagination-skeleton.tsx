import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight } from "lucide-react";

export default function PaginationSkeleton() {
  return (
    <div className="relative flex w-full items-center justify-center">
      <nav className="flex flex-wrap items-center justify-center gap-1.5" aria-hidden="true">
        <Button variant="ghost" size="icon" disabled className="pointer-events-none">
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <Button variant="ghost" size="sm" disabled className="pointer-events-none relative h-9 min-w-10 px-3">
          <span className="bg-muted/50 h-3.5 w-4 animate-pulse rounded" />
        </Button>
        <Button
          variant="default"
          size="sm"
          disabled
          className="pointer-events-none relative h-9 min-w-10 px-3"
        >
          <span className="bg-primary-foreground/30 h-3.5 w-5 animate-pulse rounded" />
        </Button>
        <Button variant="ghost" size="sm" disabled className="pointer-events-none relative h-9 min-w-10 px-3">
          <span className="bg-muted/50 h-3.5 w-4 animate-pulse rounded" />
        </Button>
        <Button variant="ghost" size="icon" disabled className="pointer-events-none">
          <ChevronRight className="h-4 w-4" />
        </Button>
      </nav>
    </div>
  );
}
