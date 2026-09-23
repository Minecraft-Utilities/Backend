import { env } from "@/common/env";
import { isProduction } from "@/common/utils";
import { ViewportProvider } from "@/components/context/viewport-context";
import Footer from "@/components/footer";
import Navbar from "@/components/navbar/navbar";
import { Toaster } from "@/components/ui/toaster";
import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import Script from "next/script";
import { QueryProvider } from "../providers/query-provider";
import "./styles/globals.css";

const siteFont = localFont({
  src: "./fonts/JetBrainsMono.ttf",
  weight: "100 300",
});

export const viewport: Viewport = { themeColor: "#9770FF" };
export const metadata: Metadata = {
  title: {
    default: "MC Utils: Minecraft Player & Server Lookup",
    template: "%s | MC Utils",
  },
  description:
    "Look up any Minecraft player by username or UUID: view skins, capes, and profile history. Check Java and Bedrock server status, player counts, and more.",
  keywords: [
    "Minecraft player lookup",
    "Minecraft skin viewer",
    "Minecraft UUID lookup",
    "check Minecraft server status",
    "Minecraft cape viewer",
    "Minecraft profile lookup",
    "Java server status",
    "Bedrock server status",
  ],
  openGraph: {
    title: "MC Utils: Minecraft Player & Server Lookup",
    siteName: "MC Utils",
    images: [
      {
        url: `${env.NEXT_PUBLIC_BASE_URL}/media/logo/logo.png`,
        width: 128,
        height: 128,
      },
    ],
  },
  icons: {
    icon: `${env.NEXT_PUBLIC_BASE_URL}/media/logo/logo.png`,
  },
  twitter: { card: "summary" },
};

export interface RootLayoutProps {
  readonly children: React.ReactNode;
}

export default function RootLayout({ children }: RootLayoutProps) {
  return (
    <html lang="en" className="h-full">
      {/* Analytics */}
      {isProduction() && (
        <Script
          src="https://analytics.fascinated.cc/script.js"
          data-website-id="83459095-69ee-4511-be2f-784217ba6a88"
          strategy="lazyOnload"
        />
      )}

      <QueryProvider>
        <ViewportProvider>
          <body className={`${siteFont.className} flex min-h-full w-full flex-col antialiased`}>
            <Navbar />
            <main id="main-content" className="relative flex min-h-0 w-full flex-1 flex-col" tabIndex={-1}>
              <Toaster />
              <div className="z-1 flex min-h-0 flex-1 flex-col gap-2">
                <div className="mx-auto w-full max-w-400 shrink-0 px-2">{children}</div>
                <div className="min-h-0 flex-1" aria-hidden />
                <Footer />
              </div>
            </main>
          </body>
        </ViewportProvider>
      </QueryProvider>
    </html>
  );
}
