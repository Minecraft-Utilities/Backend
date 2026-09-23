import { cn } from "@/common/utils";
import { ServerRegistryEntry } from "mcutils-js-api/dist/types/server-registry/server-registry-entry";

interface ServerLookupEntryProps {
  entry: ServerRegistryEntry;
  handleServerEntryClick: (entry: ServerRegistryEntry) => void;
}

export default function ServerLookupEntry({ entry, handleServerEntryClick }: ServerLookupEntryProps) {
  return (
    <div className="border-border/60 relative overflow-hidden rounded-md border">
      {entry.backgroundImageUrl && (
        <div
          className="absolute inset-0 -m-3"
          style={{
            backgroundImage: `url(${entry.backgroundImageUrl})`,
            backgroundSize: "cover",
            backgroundPosition: "center",
            filter: "blur(8px)",
          }}
          aria-hidden
        />
      )}
      <button
        type="button"
        role="option"
        aria-selected={false}
        className={cn(
          "relative z-10 flex w-full flex-col items-start gap-0.5 rounded-md px-3 py-2.5 text-left text-sm transition-colors outline-none",
          entry.backgroundImageUrl
            ? "bg-background/70 hover:bg-accent/80 focus:bg-accent/80 hover:border-border focus:border-border border border-transparent"
            : "bg-muted/30 hover:bg-accent focus:bg-accent hover:border-border focus:border-border"
        )}
        onClick={() => handleServerEntryClick(entry)}
      >
        <span className="min-w-0 truncate font-medium">{entry.displayName}</span>
        <span className="text-muted-foreground truncate text-xs">
          {entry.hostnames[0]} · {entry.platform}
        </span>
      </button>
    </div>
  );
}
