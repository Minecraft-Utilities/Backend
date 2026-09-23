"use client";

import { env } from "@/common/env";
import { Turnstile } from "@marsidev/react-turnstile";

export default function PlayerViewsCounter({ playerQuery }: { playerQuery: string }) {
  async function handleSuccess(token: string) {
    try {
      await fetch(`${env.NEXT_PUBLIC_API_URL}/players/count-view`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          playerQuery,
          turnstileToken: token,
        }),
      });
    } catch (ex) {
      console.error(ex);
    }
  }

  return (
    <Turnstile
      siteKey={env.NEXT_PUBLIC_TURNSTILE_KEY}
      onSuccess={handleSuccess}
      options={{ size: "invisible" }}
    />
  );
}
