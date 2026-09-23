import { cn } from "@/common/utils";
import type { BasicPlayer } from "mcutils-js-api/dist/types/player/player";
import Image from "next/image";

interface PlayerLookupEntryProps {
  entry: BasicPlayer;
  onSelect: (entry: BasicPlayer) => void;
}

export default function PlayerLookupEntry({ entry, onSelect }: PlayerLookupEntryProps) {
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
        src={entry.skin.parts.FACE}
        alt=""
        className="size-8 shrink-0 rounded-md object-cover"
        width={32}
        height={32}
      />
      <span className="min-w-0 truncate font-medium">{entry.username}</span>
    </button>
  );
}
