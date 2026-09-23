"use client";

import { mcUtilsApi } from "@/common/mc-utils";
import { cn, isIpOrDomain } from "@/common/utils";
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/components/ui/input-group";
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover";
import { zodResolver } from "@hookform/resolvers/zod";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useDebounce } from "@uidotdev/usehooks";
import { Clock, Loader2, Search, Server, User, X } from "lucide-react";
import type { BasicPlayer } from "mcutils-js-api/dist/types/player/player";
import { ErrorResponse } from "mcutils-js-api/dist/types/response/error-response";
import type { ServerRegistryEntry } from "mcutils-js-api/dist/types/server-registry/server-registry-entry";
import { ServerPlatform } from "mcutils-js-api/dist/types/server/server";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import PlayerLookupEntry from "../player/player-lookup-entry";
import ServerLookupEntry from "../server/server-lookup-entry";
import { Button } from "../ui/button";
import ServerEditionDialog from "./server-edition-dialog";

const HISTORY_MAX = 10;

function useLocalStorage<T>(key: string, initialValue: T): [T, (value: T | ((prev: T) => T)) => void] {
  const [storedValue, setStoredValue] = useState<T>(() => {
    if (typeof window === "undefined") return initialValue;
    try {
      const item = window.localStorage.getItem(key);
      return item !== null ? (JSON.parse(item) as T) : initialValue;
    } catch {
      return initialValue;
    }
  });

  const setValue = useCallback(
    (value: T | ((prev: T) => T)) => {
      setStoredValue(prev => {
        const next = typeof value === "function" ? (value as (prev: T) => T)(prev) : value;
        try {
          window.localStorage.setItem(key, JSON.stringify(next));
        } catch {}
        return next;
      });
    },
    [key]
  );

  useEffect(() => {
    const handler = (e: StorageEvent) => {
      if (e.key !== key) return;
      try {
        setStoredValue(e.newValue !== null ? (JSON.parse(e.newValue) as T) : initialValue);
      } catch {}
    };
    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
  }, [key, initialValue]);

  return [storedValue, setValue];
}

type HistoryEntry = { query: string; path: string };

const querySearchSchema = z.object({
  query: z
    .string()
    .transform(s => s.trim())
    .pipe(z.string().min(1, "Enter a username, UUID, or server address")),
});

type QuerySearchValues = z.infer<typeof querySearchSchema>;

export interface QuerySearchProps {
  landingPage?: boolean;
  className?: string;
  setQueryError?: (invalid: ErrorResponse | undefined) => void;
}

export default function QuerySearch({ landingPage, className, setQueryError }: QuerySearchProps) {
  const router = useRouter();
  const [serverDialogOpen, setServerDialogOpen] = useState(false);
  const [pendingServer, setPendingServer] = useState("");
  const [isLookupLoading, setIsLookupLoading] = useState(false);
  const [lookupError, setLookupError] = useState<ErrorResponse | undefined>(undefined);
  const [serverPopoverOpen, setServerPopoverOpen] = useState(false);
  const [history, setHistory] = useLocalStorage<HistoryEntry[]>("search-history", []);

  const addEntry = useCallback(
    (entry: HistoryEntry) => {
      setHistory(prev => {
        const filtered = (prev ?? []).filter(h => h.path !== entry.path);
        return [entry, ...filtered].slice(0, HISTORY_MAX);
      });
    },
    [setHistory]
  );

  const removeEntry = useCallback(
    (path: string) => {
      setHistory(prev => (prev ?? []).filter(h => h.path !== path));
    },
    [setHistory]
  );

  const form = useForm<QuerySearchValues>({
    resolver: zodResolver(querySearchSchema),
    defaultValues: { query: "" },
  });

  const queryValue = form.watch("query");
  const debouncedQuery = useDebounce(queryValue.trim(), 300);

  const {
    data: serverEntries,
    isFetching: isServerSearchFetching,
    isSuccess: isServerSearchSuccess,
  } = useQuery({
    queryKey: ["serverRegistry", debouncedQuery],
    queryFn: async (): Promise<ServerRegistryEntry[]> => {
      const result = await mcUtilsApi.fetchServerRegistryEntries(debouncedQuery);
      if (result.error) throw new Error(result.error.message);
      return result.entries ?? [];
    },
    placeholderData: keepPreviousData,
    enabled: !!debouncedQuery,
  });

  const {
    data: playerEntry,
    isFetching: isPlayerSearchFetching,
    isSuccess: isPlayerSearchSuccess,
  } = useQuery({
    queryKey: ["playerSearch", debouncedQuery],
    queryFn: async (): Promise<BasicPlayer[] | null> => {
      const result = await mcUtilsApi.searchPlayers(debouncedQuery);
      if (result.error) return null;
      return result.entries ?? null;
    },
    placeholderData: keepPreviousData,
    enabled: !!debouncedQuery,
  });

  const hasServerResults = !!serverEntries && serverEntries.length > 0;
  const hasPlayerResults = Array.isArray(playerEntry) && playerEntry.length > 0;
  const isSearching = isServerSearchFetching || isPlayerSearchFetching;
  const searchSettled = isServerSearchSuccess && isPlayerSearchSuccess;
  const showHistoryDropdown = !debouncedQuery && history.length > 0;
  const serverPopoverOpenDerived =
    (!!debouncedQuery &&
      (hasServerResults || hasPlayerResults || isSearching) &&
      (searchSettled || isSearching)) ||
    showHistoryDropdown;
  const serverPopoverOpenControlled = serverPopoverOpen && serverPopoverOpenDerived;

  const clearError = useCallback(() => {
    setLookupError(undefined);
    setQueryError?.(undefined);
  }, [setQueryError]);

  const onSubmit = useCallback(
    async (data: QuerySearchValues) => {
      const trimmed = data.query.trim();
      if (!trimmed) {
        return;
      }

      if (isIpOrDomain(trimmed)) {
        setPendingServer(trimmed);
        setServerDialogOpen(true);
        setServerPopoverOpen(false);
        return;
      }

      setLookupError(undefined);
      setQueryError?.(undefined);
      setIsLookupLoading(true);
      try {
        const result = await mcUtilsApi.fetchPlayer(trimmed);
        if (result.error) {
          setLookupError(result.error);
          setQueryError?.(result.error);
          return;
        }
        const path = `/player/${encodeURIComponent(trimmed)}`;
        addEntry({ query: trimmed, path });
        router.push(path);
        form.reset();
        setServerPopoverOpen(false);
      } catch (err) {
        throw err;
      } finally {
        setIsLookupLoading(false);
      }
    },
    [router, form, setQueryError]
  );

  const handleServerEdition = useCallback(
    (edition: ServerPlatform) => {
      if (!pendingServer) return;
      const path = `/server/${edition}/${encodeURIComponent(pendingServer)}`;
      addEntry({ query: pendingServer, path });
      router.push(path);
      form.reset();
      setPendingServer("");
      setServerDialogOpen(false);
    },
    [pendingServer, router, form, addEntry]
  );

  const handlePlayerEntryClick = useCallback(
    (entry: BasicPlayer) => {
      const path = `/player/${encodeURIComponent(entry.username)}`;
      addEntry({ query: entry.username, path });
      router.push(path);
      form.reset();
      setServerPopoverOpen(false);
    },
    [router, form, addEntry]
  );

  const handleServerEntryClick = useCallback(
    (entry: ServerRegistryEntry) => {
      const hostname = entry.hostnames[0];
      if (!hostname) return;
      const path = `/server/${entry.platform.toLowerCase()}/${encodeURIComponent(hostname)}`;
      addEntry({ query: hostname, path });
      router.push(path);
      form.reset();
      setServerPopoverOpen(false);
    },
    [router, form, addEntry]
  );

  return (
    <Popover
      open={serverPopoverOpenControlled}
      onOpenChange={open => {
        if (!open) setServerPopoverOpen(false);
        else setServerPopoverOpen(true);
      }}
    >
      <PopoverAnchor asChild>
        <form
          role="search"
          onSubmit={form.handleSubmit(onSubmit)}
          className="flex flex-col items-center gap-2 md:flex-row md:gap-0"
        >
          <InputGroup className={cn("w-full", className)}>
            <Controller
              name="query"
              control={form.control}
              render={({ field, fieldState }) => (
                <InputGroupInput
                  {...field}
                  type="text"
                  placeholder="Query..."
                  className="text-xs md:text-sm"
                  aria-invalid={fieldState.invalid || !!lookupError}
                  disabled={isLookupLoading}
                  onFocus={() => setServerPopoverOpen(true)}
                  onChange={e => {
                    field.onChange(e);
                    if (e.target.value.trim()) setServerPopoverOpen(true);
                    if (lookupError) clearError();
                  }}
                />
              )}
            />

            <InputGroupAddon>
              {isLookupLoading ? (
                <Loader2 className="text-muted-foreground size-4 animate-spin" />
              ) : (
                <Search className="text-muted-foreground size-4" />
              )}
            </InputGroupAddon>

            <InputGroupAddon align="inline-end" className="min-w-9">
              <InputGroupButton
                type="button"
                size="icon-xs"
                variant="ghost"
                aria-label="Clear search"
                disabled={isLookupLoading}
                className={cn(queryValue.length === 0 && "pointer-events-none invisible")}
                onClick={() => {
                  form.setValue("query", "");
                  clearError();
                }}
              >
                <X className="size-4" />
              </InputGroupButton>
            </InputGroupAddon>
          </InputGroup>

          {landingPage && (
            <Button
              type="submit"
              className="block w-full text-xs md:hidden md:text-sm"
              disabled={isLookupLoading}
            >
              Search
            </Button>
          )}

          <ServerEditionDialog
            open={serverDialogOpen}
            onOpenChange={setServerDialogOpen}
            serverAddress={pendingServer}
            onSelectEdition={handleServerEdition}
          />
        </form>
      </PopoverAnchor>
      <PopoverContent
        className="max-h-72 w-(--radix-popover-trigger-width) min-w-(--radix-popover-trigger-width) overflow-y-auto p-0"
        align="start"
        role="listbox"
        onOpenAutoFocus={e => e.preventDefault()}
      >
        <div className="flex flex-col gap-1 overflow-y-auto p-1">
          {showHistoryDropdown ? (
            <section className="flex flex-col gap-1" aria-label="Recent searches">
              <div className="text-muted-foreground flex items-center gap-2 px-3 py-1.5">
                <Clock className="size-3.5 shrink-0" aria-hidden />
                <span className="text-xs font-medium tracking-wider uppercase">Recent</span>
              </div>
              <ul className="flex flex-col gap-0.5">
                {history.map(entry => (
                  <li key={entry.path} className="flex items-center gap-1">
                    <button
                      type="button"
                      className="border-border/60 bg-muted/30 hover:border-border hover:bg-accent flex min-w-0 flex-1 items-center gap-3 rounded-md border px-3 py-2.5 text-left text-sm transition-colors outline-none"
                      onClick={() => {
                        addEntry(entry);
                        router.push(entry.path);
                        setServerPopoverOpen(false);
                      }}
                    >
                      <Clock className="text-muted-foreground size-3.5 shrink-0" aria-hidden />
                      <span className="min-w-0 flex-1 truncate">{entry.query}</span>
                    </button>
                    <button
                      type="button"
                      aria-label={`Remove ${entry.query} from history`}
                      className="text-muted-foreground hover:text-foreground shrink-0 rounded p-1"
                      onClick={() => removeEntry(entry.path)}
                    >
                      <X className="size-3.5" />
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          {!showHistoryDropdown && playerEntry && playerEntry.length > 0 ? (
            <section className="flex flex-col gap-1" aria-label="Players">
              <div className="text-muted-foreground flex items-center gap-2 px-3 py-1.5">
                <User className="size-3.5 shrink-0" aria-hidden />
                <span className="text-xs font-medium tracking-wider uppercase">Players</span>
              </div>
              <ul className="flex flex-col gap-0.5">
                {playerEntry.map(entry => (
                  <li key={entry.uniqueId}>
                    <PlayerLookupEntry entry={entry} onSelect={handlePlayerEntryClick} />
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          {!showHistoryDropdown && serverEntries && serverEntries.length > 0 ? (
            <section className="flex flex-col gap-1" aria-label="Servers">
              <div className="text-muted-foreground flex items-center gap-2 px-3 py-1.5">
                <Server className="size-3.5 shrink-0" aria-hidden />
                <span className="text-xs font-medium tracking-wider uppercase">Servers</span>
              </div>
              <ul className="flex flex-col gap-0.5">
                {serverEntries.map(entry => (
                  <li key={entry.serverId}>
                    <ServerLookupEntry
                      entry={entry}
                      handleServerEntryClick={() => handleServerEntryClick(entry)}
                    />
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      </PopoverContent>
    </Popover>
  );
}
