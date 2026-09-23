"use client";

import { FullPlayer } from "mcutils-js-api/dist/types/player/player";
import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from "react";
import {
  FlyingAnimation,
  IdleAnimation,
  RunningAnimation,
  WalkingAnimation,
  WaveAnimation,
  type PlayerAnimation,
  type Render,
} from "skin3d";

/** UI-facing animation descriptor for skin3d */
export type Skin3DAnimation = {
  name: string;
  animation: PlayerAnimation;
};

/** Built-in animation instances for skin3d (stable references) */
export const skin3DAnimations = {
  idle: { name: "idle", animation: new IdleAnimation() },
  walking: { name: "walking", animation: new WalkingAnimation() },
  running: { name: "running", animation: new RunningAnimation() },
  flying: { name: "flying", animation: new FlyingAnimation() },
  wave: { name: "wave", animation: new WaveAnimation() },
} as const;

type Skin3DSettingsContextType = {
  /** The currently selected animation. */
  animation: Skin3DAnimation;
  /** Whether to show the elytra layer. */
  showElytra: boolean;
  /** Whether to show outer skin layers. */
  showLayers: boolean;
  /** Update the skin viewer reference for controls. */
  updateSkinViewerRef: (skinViewer: Render | null) => void;
  /** Play an animation. */
  playAnimation: (animation: Skin3DAnimation) => void;
  /** Toggle showing the elytra layer. */
  toggleShowElytra: () => void;
  /** Toggle showing outer skin layers. */
  toggleShowLayers: () => void;
};

const Skin3DSettingsContext = createContext<Skin3DSettingsContextType | undefined>(undefined);

export function Skin3DSettingsProvider({ player, children }: { player: FullPlayer; children: ReactNode }) {
  const skinViewerRef: RefObject<Render | null> = useRef<Render | null>(null);
  const [selectedAnimation, setSelectedAnimation] = useState<Skin3DAnimation>(skin3DAnimations.idle);
  const [showElytra, setShowElytra] = useState<boolean>(false);
  const [showLayers, setShowLayers] = useState<boolean>(true);

  function playAnimation(animation: Skin3DAnimation) {
    setSelectedAnimation(animation);
  }

  // Sync selected animation to the viewer
  useEffect(() => {
    if (skinViewerRef.current) {
      skinViewerRef.current.animation = selectedAnimation.animation;
    }
  }, [selectedAnimation]);

  // Sync outer layer visibility with the viewer
  useEffect(() => {
    if (skinViewerRef.current) {
      skinViewerRef.current.playerObject.skin.setOuterLayerVisible(showLayers);
    }
  }, [showLayers]);

  const effectiveShowElytra: boolean = showElytra || selectedAnimation.name === skin3DAnimations.flying.name;

  useEffect(() => {
    if (skinViewerRef.current && player.cape) {
      skinViewerRef.current.playerObject.backEquipment = effectiveShowElytra ? "elytra" : "cape";
    }
  }, [player.cape, effectiveShowElytra]);

  function toggleShowLayers() {
    const next = !showLayers;
    setShowLayers(next);
    if (skinViewerRef.current) {
      skinViewerRef.current.playerObject.skin.setOuterLayerVisible(next);
    }
  }

  function updateSkinViewerRef(skinViewer: Render | null) {
    skinViewerRef.current = skinViewer;
  }

  function toggleShowElytra() {
    setShowElytra(function (prev) {
      return !prev;
    });
  }

  return (
    <Skin3DSettingsContext.Provider
      value={{
        animation: selectedAnimation,
        showElytra,
        showLayers,
        updateSkinViewerRef,
        playAnimation,
        toggleShowElytra,
        toggleShowLayers,
      }}
    >
      {children}
    </Skin3DSettingsContext.Provider>
  );
}

export function useSkin3DSettings() {
  const context = useContext(Skin3DSettingsContext);
  if (!context) {
    throw new Error("useSkin3DSettings must be used within a Skin3DSettingsProvider");
  }
  return context;
}
