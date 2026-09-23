"use client";

import SimpleLink from "@/components/simple-link";
import { Button } from "@/components/ui/button";
import { HomeIcon } from "lucide-react";
import { useEffect } from "react";

export default function PagesError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="relative flex w-full flex-col items-center justify-center pt-24 text-center">
      <div className="z-10 flex flex-col items-center gap-8">
        <div className="flex flex-col items-center gap-4">
          <h1 className="text-destructive text-3xl font-bold sm:text-4xl">Something went wrong</h1>
          <p className="text-muted-foreground max-w-md text-lg">
            An error occurred while loading this page. You can try again or return home.
          </p>
        </div>
        <div className="flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
          <Button size="lg" variant="outline" onClick={reset}>
            Try again
          </Button>
          <SimpleLink href="/">
            <Button
              size="lg"
              className="border-primary/30 bg-primary/5 text-primary hover:border-primary/50 hover:bg-primary/10"
            >
              <HomeIcon className="mr-2 size-5" />
              Return Home
            </Button>
          </SimpleLink>
        </div>
      </div>
    </div>
  );
}
