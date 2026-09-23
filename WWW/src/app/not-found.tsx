import SimpleLink from "@/components/simple-link";
import { Button } from "@/components/ui/button";
import { HomeIcon } from "lucide-react";

export default function NotFoundPage() {
  return (
    <div className="relative flex w-full flex-col items-center justify-center pt-24 text-center">
      {/* 404 Content */}
      <div className="z-10 flex flex-col items-center gap-8">
        {/* Main Content */}
        <div className="flex flex-col items-center gap-4">
          <h1 className="text-destructive text-3xl font-bold sm:text-4xl lg:text-5xl">Page Not Found</h1>
          <p className="text-muted-foreground max-w-md text-lg">
            The page you&apos;re looking for doesn&apos;t exist or has been moved. Don&apos;t worry, you can
            always find your way back!
          </p>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
          <SimpleLink href="/">
            <Button
              size="lg"
              className="border-primary/30 bg-primary/5 text-primary hover:border-primary/50 hover:bg-primary/10 group relative h-12 overflow-hidden rounded-xl border-2 px-8 backdrop-blur-sm transition-all duration-300"
            >
              <div className="from-primary/10 to-accent-secondary/10 absolute inset-0 bg-linear-to-r opacity-0 transition-opacity duration-300 group-hover:opacity-100" />
              <HomeIcon className="relative z-10 mr-2 size-5" />
              <span className="relative z-10 font-semibold">Return Home</span>
            </Button>
          </SimpleLink>
        </div>
      </div>
    </div>
  );
}
