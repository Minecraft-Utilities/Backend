"use client";

import { skin3DAnimations, useSkin3DSettings } from "@/components/provider/skin-3d-settings-provider";
import SimpleTooltip from "@/components/simple-tooltip";
import { Button } from "@/components/ui/button";
import { LayersIcon } from "lucide-react";
import { FullPlayer } from "mcutils-js-api/dist/types/player/player";
import Image from "next/image";
import type { ReactNode } from "react";

export interface Skin3DButtonsProps {
  player: FullPlayer;
}

export default function Skin3DButtons({ player }: Skin3DButtonsProps) {
  const { animation, showElytra, showLayers, playAnimation, toggleShowElytra, toggleShowLayers } =
    useSkin3DSettings();

  return (
    <div className="absolute right-1 flex flex-col gap-1">
      {/* Animation & 3D controls */}
      <SkinActionButton
        tooltip="Play Idle Animation"
        icon="/media/3d-skin-buttons/steve.webp"
        isSelected={animation.name === skin3DAnimations.idle.name}
        onClick={() => playAnimation(skin3DAnimations.idle)}
      />
      <SkinActionButton
        tooltip="Play Walk Animation"
        icon="/media/3d-skin-buttons/steve-walking.webp"
        isSelected={animation.name === skin3DAnimations.walking.name}
        onClick={() => playAnimation(skin3DAnimations.walking)}
      />
      <SkinActionButton
        tooltip="Play Fly Animation"
        icon="/media/3d-skin-buttons/feather.webp"
        isSelected={animation.name === skin3DAnimations.flying.name}
        onClick={() => playAnimation(skin3DAnimations.flying)}
      />
      {player.cape && (
        <SkinActionButton
          tooltip="Toggle Elytra"
          icon="/media/3d-skin-buttons/elytra.webp"
          isSelected={showElytra || animation.name === skin3DAnimations.flying.name}
          onClick={toggleShowElytra}
        />
      )}
      <SkinActionButton
        tooltip="Toggle Layers"
        icon={<LayersIcon className="size-3.5" />}
        isSelected={showLayers}
        onClick={toggleShowLayers}
      />
    </div>
  );
}

function SkinActionButton({
  tooltip,
  icon,
  isSelected,
  onClick,
}: {
  tooltip: string;
  icon: string | ReactNode;
  isSelected?: boolean;
  onClick: () => void;
}) {
  const button = (
    <Button variant={isSelected ? "default" : "outline"} size="icon" className="size-8" onClick={onClick}>
      {typeof icon === "string" ? (
        <Image className="object-contain" src={icon} alt={tooltip} width={14} height={14} draggable={false} />
      ) : (
        icon
      )}
    </Button>
  );

  if (isSelected) return button;

  return (
    <SimpleTooltip display={tooltip} side="right" className="w-auto">
      {button}
    </SimpleTooltip>
  );
}
