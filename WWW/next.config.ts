import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  reactCompiler: true,
  poweredByHeader: false,
  experimental: {
    optimizePackageImports: ["lucide-react", "@radix-ui/react-icons", "@radix-ui/react-popper"],
  },
  images: {
    unoptimized: true,
  },
  logging: {
    fetches: {
      fullUrl: true,
    },
  },
};

export default nextConfig;
