"use client";

import { useSelectedCape } from "@/components/provider/selected-cape-provider";
import { useSelectedSkin } from "@/components/provider/selected-skin-provider";
import { useSkin3DSettings } from "@/components/provider/skin-3d-settings-provider";
import { FullPlayer } from "mcutils-js-api/dist/types/player/player";
import { useEffect, useRef } from "react";
import { Render } from "skin3d";

export interface PlayerSkin3DProps {
  player: FullPlayer;
}

export default function PlayerSkin3D({ player }: PlayerSkin3DProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const viewerRef = useRef<Render | null>(null);
  const { updateSkinViewerRef } = useSkin3DSettings();
  const { selectedSkin } = useSelectedSkin();
  const { selectedCape } = useSelectedCape();
  const selectedCapeRef = useRef(selectedCape);
  selectedCapeRef.current = selectedCape;

  // Initialize the viewer (recreate only when the skin changes)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const viewer = new Render({
      canvas,
      width: 230,
      height: 288,
      allowZoom: false,
    });

    viewer.loadSkin(selectedSkin.textureUrl);
    if (selectedCapeRef.current) {
      viewer.loadCape(selectedCapeRef.current.textureUrl);
    }

    viewerRef.current = viewer;
    updateSkinViewerRef(viewer);

    return () => {
      viewerRef.current = null;
      updateSkinViewerRef(null);
      viewer.dispose();
    };
  }, [selectedSkin.textureUrl, updateSkinViewerRef]);

  // Update the cape on the existing viewer without recreating it
  useEffect(() => {
    if (!viewerRef.current) return;
    if (selectedCape) {
      viewerRef.current.loadCape(selectedCape.textureUrl);
    } else {
      viewerRef.current.loadCape(null);
    }
  }, [selectedCape]);

  return <canvas ref={canvasRef} className="rounded-lg" />;
}
