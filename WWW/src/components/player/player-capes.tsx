"use client";

import { cn } from "@/common/utils";
import { FullPlayer } from "mcutils-js-api/dist/types/player/player";
import Image from "next/image";
import { useRef } from "react";
import { useSelectedCape } from "../provider/selected-cape-provider";
import SimpleTooltip from "../simple-tooltip";
import TimeAgo from "../time-ago";
import Card, { CardContent, CardHeader } from "../ui/card";

export interface PlayerCapesProps {
  player: FullPlayer;
}

export const CAPE_ASPECT_RATIO = 480 / 768;

export default function PlayerCapes({ player }: PlayerCapesProps) {
  const { selectedCape, setSelectedCape, setHoveredCape } = useSelectedCape();
  const hoverClearTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const capes =
    player.capeHistory?.sort((a, b) => new Date(b.firstSeen).getTime() - new Date(a.firstSeen).getTime()) ??
    [];

  function handleCapeHoverEnter(cape: (typeof capes)[number]) {
    if (hoverClearTimeout.current) clearTimeout(hoverClearTimeout.current);
    setHoveredCape(cape);
  }

  function handleCapeHoverLeave() {
    hoverClearTimeout.current = setTimeout(() => {
      setHoveredCape(null);
    }, 80);
  }

  return (
    <Card className="h-fit min-w-48 overflow-hidden p-0">
      <CardHeader>Capes ({capes.length})</CardHeader>
      <CardContent className="flex h-full items-center justify-center gap-2">
        {capes.length > 0 ? (
          <>
            {capes.map(cape => {
              const isSelected = selectedCape?.textureId === cape.textureId;

              return (
                <SimpleTooltip
                  key={cape.textureId}
                  display={
                    <div className="flex flex-col gap-2">
                      <p className={cn("text-sm font-medium", isSelected ? "text-primary" : "")}>
                        {isSelected ? "Currently selected" : cape.name ? `${cape.name} Cape` : "Unknown Cape"}
                      </p>
                      <div className="flex flex-col gap-0.5">
                        <div className="flex items-center justify-between gap-4 text-xs">
                          <span className="text-muted-foreground">First Seen</span>
                          <span className="font-medium">
                            <TimeAgo date={new Date(cape.firstSeen)} />
                          </span>
                        </div>
                      </div>
                    </div>
                  }
                >
                  <button
                    onClick={() => setSelectedCape(cape)}
                    onMouseEnter={() => handleCapeHoverEnter(cape)}
                    onMouseLeave={handleCapeHoverLeave}
                  >
                    <Image
                      src={cape.parts.FRONT}
                      alt={`${player.username} cape`}
                      width={64 * CAPE_ASPECT_RATIO}
                      height={64}
                      className={cn(
                        "h-16 rounded-sm object-cover",
                        isSelected ? "border-primary border-2" : ""
                      )}
                    />
                  </button>
                </SimpleTooltip>
              );
            })}
          </>
        ) : (
          <div className="flex h-full min-h-16 flex-col items-center justify-center">
            <p className="text-muted-foreground text-center text-sm">No capes available</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
