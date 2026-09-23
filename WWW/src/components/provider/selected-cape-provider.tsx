"use client";

import { Cape } from "mcutils-js-api/dist/types/player/cape/cape";
import { createContext, ReactNode, useContext, useState } from "react";

type SelectedCapeContextProps = {
  selectedCape: Cape | null;
  setSelectedCape: (cape: Cape | null) => void;
  hoveredCape: Cape | null;
  setHoveredCape: (cape: Cape | null) => void;
};
const SelectedCapeContext = createContext<SelectedCapeContextProps | undefined>(undefined);

export const SelectedCapeProvider = ({
  children,
  initialCape,
}: {
  children: ReactNode;
  initialCape: Cape | null;
}) => {
  const [selectedCape, setSelectedCape] = useState<Cape | null>(initialCape);
  const [hoveredCape, setHoveredCape] = useState<Cape | null>(null);

  return (
    <SelectedCapeContext.Provider value={{ selectedCape, setSelectedCape, hoveredCape, setHoveredCape }}>
      {children}
    </SelectedCapeContext.Provider>
  );
};

export const useSelectedCape = (): SelectedCapeContextProps => {
  const context = useContext(SelectedCapeContext);
  if (!context) {
    throw new Error("useSelectedCape must be used within a SelectedCapeProvider");
  }
  return context;
};
