"use client";

import { createContext, ReactNode, useContext, useEffect, useLayoutEffect, useState } from "react";

// Tailwind breakpoints matching the project's theme (internal use; useIsMobile accepts breakpoint key)
const Breakpoint = {
  xxs: 320,
  xs: 475,
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  "2xl": 1536,
} as const;

type BreakpointKey = keyof typeof Breakpoint;

interface ViewportContextType {
  width: number;
  height: number;
}

const ViewportContext = createContext<ViewportContextType | null>(null);

export interface ViewportProviderProps {
  children: ReactNode;
}

export function ViewportProvider({ children }: ViewportProviderProps) {
  const [viewport, setViewport] = useState<ViewportContextType>(getInitialViewport);

  useIsomorphicLayoutEffect(() => {
    const handleResize = () => {
      const dimensions = getWindowDimensions();
      const next: ViewportContextType = {
        width: dimensions.width,
        height: dimensions.height,
      };

      setViewport(next);
    };

    // Initialize on mount
    handleResize();

    // Add resize listener
    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
    };
  }, []);

  return <ViewportContext.Provider value={viewport}>{children}</ViewportContext.Provider>;
}

const useIsomorphicLayoutEffect = typeof window !== "undefined" ? useLayoutEffect : useEffect;

// Always use a fixed initial value so server and first client render match (avoids hydration errors).
// Real dimensions are set in useLayoutEffect after mount.
function getInitialViewport(): ViewportContextType {
  return { width: 0, height: 0 };
}

function getWindowDimensions() {
  if (typeof window === "undefined") {
    return { width: 0, height: 0 };
  }
  const { innerWidth: width, innerHeight: height } = window;
  return { width, height };
}

export function useViewport() {
  const context = useContext(ViewportContext);
  if (!context) {
    throw new Error("useViewport must be used within a ViewportProvider");
  }
  return context;
}

export function useIsMobile(breakpoint?: BreakpointKey): boolean {
  const { width } = useViewport();
  breakpoint = breakpoint ?? "md";
  return width <= Breakpoint[breakpoint];
}

export function useWindowDimensions() {
  const { width, height } = useViewport();
  return { width, height };
}
