"use client";

import { cn } from "@/common/utils";
import { useSelectedCape } from "@/components/provider/selected-cape-provider";
import { useSelectedSkin } from "@/components/provider/selected-skin-provider";
import { Skin3DSettingsProvider } from "@/components/provider/skin-3d-settings-provider";
import SimpleTooltip from "@/components/simple-tooltip";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import DownloadFileButton from "@/components/ui/download-file-button";
import { DownloadIcon } from "lucide-react";
import { FullPlayer } from "mcutils-js-api/dist/types/player/player";
import { SkinPart } from "mcutils-js-api/dist/types/player/skin/skin-part";
import Image from "next/image";
import { useRef, useState } from "react";
import PlayerSkin3D from "./player-skin-3d";
import Skin3DButtons from "./skin-3d-buttons";

const SKIN_MODES = ["3D", "2D"];

export interface PlayerSkinProps {
  player: FullPlayer;
}

export default function PlayerSkin({ player }: PlayerSkinProps) {
  const { selectedSkin, hoveredSkin } = useSelectedSkin();
  const { selectedCape, hoveredCape } = useSelectedCape();

  const displaySkin = hoveredSkin ?? selectedSkin;
  const displayCape = hoveredCape ?? selectedCape;

  const [selectedMode, setSelectedMode] = useState<(typeof SKIN_MODES)[number]>("2D");
  const [selectedPart, setSelectedPart] = useState<SkinPart>("FULLBODY_ISO_FRONT");
  const [hoveredPart, setHoveredPart] = useState<SkinPart | null>(null);
  const [hoveredMode, setHoveredMode] = useState<string | null>(null);
  const hoverClearTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  const displayMode = hoveredMode ?? selectedMode;
  const displayPart = hoveredPart ?? selectedPart;

  function handlePartHoverEnter(part: SkinPart) {
    if (hoverClearTimeout.current) clearTimeout(hoverClearTimeout.current);
    setHoveredPart(part);
    setHoveredMode("2D");
  }

  function handlePartHoverLeave() {
    hoverClearTimeout.current = setTimeout(() => {
      setHoveredPart(null);
      setHoveredMode(null);
    }, 80);
  }

  return (
    <Card className="overflow-hidden p-0">
      <CardHeader>Skin</CardHeader>
      <CardContent className="relative flex flex-col items-center gap-4">
        <div className="relative flex h-72 w-full items-center justify-center overflow-hidden">
          {displayMode === "2D" && (
            <Image
              src={`${displaySkin.parts[displayPart]}?capeId=${displayCape?.id ?? ""}`}
              alt={`${player.username} skin - ${displayPart}`}
              width={256}
              height={256}
              className="max-h-full max-w-full object-contain"
            />
          )}
          {displayMode === "3D" && (
            <Skin3DSettingsProvider player={player}>
              <PlayerSkin3D player={player} />
              <Skin3DButtons player={player} />
            </Skin3DSettingsProvider>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-center gap-2">
          <SkinSelectionButton selected={selectedMode === "3D"} onClick={() => setSelectedMode("3D")}>
            <p className="font-bold">3D</p>
          </SkinSelectionButton>

          {Object.keys(displaySkin.parts)
            .sort(key => (key.includes("ISO") ? -1 : 1))
            .map(key => {
              return (
                <SkinSelectionButton
                  key={key}
                  selected={selectedPart === key && selectedMode === "2D"}
                  onClick={() => {
                    setSelectedPart(key as SkinPart);
                    setSelectedMode("2D");
                  }}
                  onMouseEnter={() => handlePartHoverEnter(key as SkinPart)}
                  onMouseLeave={handlePartHoverLeave}
                >
                  <Image
                    src={`${displaySkin.parts[key as SkinPart]}?capeId=${displayCape?.id ?? ""}`}
                    alt={key}
                    width={56}
                    height={56}
                    className="h-full w-full object-contain object-center p-1.5"
                  />
                </SkinSelectionButton>
              );
            })}
        </div>

        {/* Download Buttons */}
        <div className="absolute top-2 right-2 flex gap-2">
          <SimpleTooltip display="Download Skin Texture">
            <DownloadFileButton
              href={displaySkin.textureUrl}
              filename={`${player.username}-skin-texture.png`}
            >
              <DownloadIcon className="size-4" />
            </DownloadFileButton>
          </SimpleTooltip>
        </div>
      </CardContent>
    </Card>
  );
}

interface SkinSelectionButtonProps {
  children: React.ReactNode;
  selected: boolean;
  onClick: () => void;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
}

function SkinSelectionButton({
  children,
  selected,
  onClick,
  onMouseEnter,
  onMouseLeave,
}: SkinSelectionButtonProps) {
  return (
    <button
      onClick={onClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      className={cn(
        "bg-muted/30 flex overflow-hidden rounded-lg border-2 transition-colors",
        "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
        "size-10 items-center justify-center",
        selected ? "border-primary" : "border-border/60 hover:border-border"
      )}
    >
      {children}
    </button>
  );
}
