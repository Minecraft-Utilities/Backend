"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";

export interface QueryProviderProps {
  children: ReactNode;
}

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 1000 * 30, // 30 seconds
        refetchInterval: 1000 * 60, // 1 minute
        refetchIntervalInBackground: false,
        retry: true,
        retryDelay: 1000 * 5, // 5 seconds
      },
    },
  });
}

export function QueryProvider({ children }: QueryProviderProps) {
  const [queryClient] = useState(makeQueryClient);
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
