import { createEnv } from "@t3-oss/env-core";
import { z } from "zod";

export const env = createEnv({
  server: {
    PLAYER_VIEW_AUTH_TOKEN: z.string(),
  },

  client: {
    NEXT_PUBLIC_API_URL: z.string(),
    NEXT_PUBLIC_BASE_URL: z.string(),
    NEXT_PUBLIC_ENVIRONMENT: z.string(),
    NEXT_PUBLIC_TURNSTILE_KEY: z.string(),
  },

  runtimeEnv: {
    PLAYER_VIEW_AUTH_TOKEN: process.env.PLAYER_VIEW_AUTH_TOKEN,
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
    NEXT_PUBLIC_BASE_URL: process.env.NEXT_PUBLIC_BASE_URL,
    NEXT_PUBLIC_ENVIRONMENT: process.env.NEXT_PUBLIC_ENVIRONMENT,
    NEXT_PUBLIC_TURNSTILE_KEY: process.env.NEXT_PUBLIC_TURNSTILE_KEY,
  },

  clientPrefix: "NEXT_PUBLIC_",
  emptyStringAsUndefined: true,
});
