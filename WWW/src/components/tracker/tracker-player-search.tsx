"use client";

import { mcUtilsApi } from "@/common/mc-utils";
import { cn } from "@/common/utils";
import PlayerLookupEntry from "@/components/player/player-lookup-entry";
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/components/ui/input-group";
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useDebounce } from "@uidotdev/usehooks";
import { CircleAlert, Loader2, Search, UserRound, X } from "lucide-react";
import type { BasicPlayer } from "mcutils-js-api/dist/types/player/player";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useState } from "react";

export interface TrackerPlayerSearchProps {
  className?: string;
}

export default function TrackerPlayerSearch({ className }: TrackerPlayerSearchProps) {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const debouncedQuery = useDebounce(query.trim(), 300);

  const {
    data: entries = [],
    error,
    isError,
    isFetching,
    isSuccess,
  } = useQuery({
    queryKey: ["playerSearch", debouncedQuery],
    queryFn: async (): Promise<BasicPlayer[]> => {
      const result = await mcUtilsApi.searchPlayers(debouncedQuery);
      if (result.error) {
        throw new Error(result.error.message);
      }
      return result.entries ?? [];
    },
    placeholderData: keepPreviousData,
    enabled: debouncedQuery.length > 0,
  });

  const selectPlayer = useCallback(
    (entry: BasicPlayer) => {
      setOpen(false);
      setQuery("");
      router.push(`/servers/players/${entry.uniqueId}`);
    },
    [router]
  );

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const exact = entries.find(entry => entry.username.toLowerCase() === debouncedQuery.toLowerCase());
    const target = exact ?? entries[0];
    if (target) {
      selectPlayer(target);
    }
  };

  const showEmpty = debouncedQuery.length > 0 && isSuccess && entries.length === 0;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverAnchor asChild>
        <form role="search" onSubmit={handleSubmit} className={cn("relative w-full", className)}>
          <InputGroup className="h-12">
            <InputGroupAddon>
              {isFetching ? (
                <Loader2 className="text-muted-foreground size-4 animate-spin" aria-hidden />
              ) : (
                <Search className="text-muted-foreground size-4" aria-hidden />
              )}
            </InputGroupAddon>
            <InputGroupInput
              type="search"
              value={query}
              placeholder="Search by username..."
              autoComplete="off"
              spellCheck={false}
              aria-label="Search players by username"
              aria-expanded={open}
              aria-controls="tracker-player-search-results"
              onFocus={() => setOpen(true)}
              onChange={event => {
                setQuery(event.target.value);
                setOpen(true);
              }}
            />
            <InputGroupAddon align="inline-end">
              <InputGroupButton
                type="button"
                size="icon-sm"
                variant="ghost"
                aria-label="Clear player search"
                className={cn(query.length === 0 && "pointer-events-none invisible")}
                onClick={() => {
                  setQuery("");
                  setOpen(false);
                }}
              >
                <X className="size-4" />
              </InputGroupButton>
            </InputGroupAddon>
          </InputGroup>
        </form>
      </PopoverAnchor>

      <PopoverContent
        id="tracker-player-search-results"
        className="max-h-80 w-(--radix-popover-trigger-width) min-w-(--radix-popover-trigger-width) overflow-y-auto p-1"
        align="center"
        role="listbox"
        onOpenAutoFocus={event => event.preventDefault()}
      >
        {entries.length > 0 ? (
          <div className="flex flex-col gap-1 p-1">
            <div className="text-muted-foreground flex items-center gap-2 px-3 py-1.5">
              <UserRound className="size-3.5" aria-hidden />
              <span className="text-xs font-medium tracking-wider uppercase">Players</span>
            </div>
            {entries.map(entry => (
              <PlayerLookupEntry key={entry.uniqueId} entry={entry} onSelect={selectPlayer} />
            ))}
          </div>
        ) : showEmpty ? (
          <div className="text-muted-foreground flex items-center gap-3 px-4 py-5 text-sm">
            <UserRound className="size-4 shrink-0" aria-hidden />
            No players match “{debouncedQuery}”.
          </div>
        ) : isError ? (
          <div className="text-destructive flex items-center gap-3 px-4 py-5 text-sm" role="alert">
            <CircleAlert className="size-4 shrink-0" aria-hidden />
            {error instanceof Error ? error.message : "Player search is temporarily unavailable."}
          </div>
        ) : (
          <div className="text-muted-foreground flex items-center gap-3 px-4 py-5 text-sm">
            <Search className="size-4 shrink-0" aria-hidden />
            Start typing a Minecraft username.
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
