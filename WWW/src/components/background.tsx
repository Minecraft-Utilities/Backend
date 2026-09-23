"use client";

import Image from "next/image";
import { useState } from "react";

interface BackgroundProps {
  url: string;
}

const height = 500;
export default function Background({ url }: BackgroundProps) {
  const [loaded, setLoaded] = useState(false);

  return (
    <div
      className="pointer-events-none absolute inset-x-0 -z-50 overflow-hidden select-none"
      style={{
        top: "calc(-1 * var(--nav-height))",
        height: "calc(500px + var(--nav-height))",
      }}
      aria-hidden
    >
      <Image
        src={url}
        alt="Background"
        width={1920}
        height={height}
        sizes="100vw"
        className={`h-full w-full object-cover blur-xs brightness-80 transition-opacity duration-500 ${
          loaded ? "opacity-100" : "opacity-0"
        }`}
        fetchPriority="high"
        priority
        onLoad={() => setLoaded(true)}
      />
      <div
        className="absolute inset-0 bg-[linear-gradient(to_bottom,transparent_40%,var(--background)_100%),linear-gradient(to_bottom,transparent_0%,transparent_10%,rgba(0,0,0,0.5)_100%)]"
        aria-hidden
      />
    </div>
  );
}
