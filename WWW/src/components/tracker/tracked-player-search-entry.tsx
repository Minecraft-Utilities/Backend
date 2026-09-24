import { env } from "@/common/env";
import type { TrackedPlayerSearchResult } from "@/common/tracker";
import { cn } from "@/common/utils";
import Image from "next/image";

interface TrackedPlayerSearchEntryProps {
  entry: TrackedPlayerSearchResult;
  onSelect: (entry: TrackedPlayerSearchResult) => void;
}

export default function TrackedPlayerSearchEntry({ entry, onSelect }: TrackedPlayerSearchEntryProps) {
  return (
    <button
      type="button"
      role="option"
      aria-selected={false}
      className={cn(
        "border-border/60 bg-muted/30 flex w-full items-center gap-3 rounded-md border px-3 py-2.5 text-left text-sm transition-colors outline-none",
        "hover:border-border hover:bg-accent focus:border-border focus:bg-accent"
      )}
      onClick={() => onSelect(entry)}
    >
      <Image
        src={`${env.NEXT_PUBLIC_API_URL.replace(/\/$/, "")}/skins/${entry.skinId}/texture.png`}
        alt=""
        className="size-8 shrink-0 rounded-md object-cover"
        width={32}
        height={32}
      />
      <span className="min-w-0 truncate font-medium">{entry.username}</span>
    </button>
  );
}
