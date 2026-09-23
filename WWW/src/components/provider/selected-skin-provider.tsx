"use client";

import { Skin } from "mcutils-js-api/dist/types/player/skin/skin";
import { createContext, ReactNode, useContext, useState } from "react";

type SelectedSkinContextProps = {
  selectedSkin: Skin;
  setSelectedSkin: (skin: Skin) => void;
  hoveredSkin: Skin | null;
  setHoveredSkin: (skin: Skin | null) => void;
};
const SelectedSkinContext = createContext<SelectedSkinContextProps | undefined>(undefined);

export const SelectedSkinProvider = ({
  children,
  initialSkin,
}: {
  children: ReactNode;
  initialSkin: Skin;
}) => {
  const [selectedSkin, setSelectedSkin] = useState<Skin>(initialSkin);
  const [hoveredSkin, setHoveredSkin] = useState<Skin | null>(null);

  return (
    <SelectedSkinContext.Provider value={{ selectedSkin, setSelectedSkin, hoveredSkin, setHoveredSkin }}>
      {children}
    </SelectedSkinContext.Provider>
  );
};

export const useSelectedSkin = (): SelectedSkinContextProps => {
  const context = useContext(SelectedSkinContext);
  if (!context) {
    throw new Error("useSelectedSkin must be used within a SelectedSkinProvider");
  }
  return context;
};
