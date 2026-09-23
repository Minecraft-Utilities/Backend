"use client";

import { env } from "@/common/env";
import { mcUtilsApi } from "@/common/mc-utils";
import { formatDate } from "@/common/time-utils";
import SimpleLink from "@/components/simple-link";
import SimpleTooltip from "@/components/simple-tooltip";
import TimeAgo from "@/components/time-ago";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRightIcon } from "lucide-react";
import { RecentUsernameChange } from "mcutils-js-api/dist/types/player/recent-username-change";
import Image from "next/image";
import { useEffect, useRef } from "react";
import useWebSocket from "react-use-websocket";

const TOTAL_NAME_CHANGES_TO_SHOW = 50;
const RECENT_NAME_CHANGES_QUERY_KEY = ["recentNameChanges"] as const;

export default function NameChanges({
  nameChanges: initialNameChanges,
}: {
  nameChanges: RecentUsernameChange[];
}) {
  const queryClient = useQueryClient();
  const hasConnectedRef = useRef(false);

  const { data: nameChanges = initialNameChanges, refetch, dataUpdatedAt } = useQuery({
    queryKey: RECENT_NAME_CHANGES_QUERY_KEY,
    queryFn: async () => {
      const { nameChanges, error } = await mcUtilsApi.getRecentNameChanges();
      if (error) throw error;
      return nameChanges ?? [];
    },
    initialData: initialNameChanges,
  });

  const { lastJsonMessage } = useWebSocket<RecentUsernameChange>(
    "wss://mc.fascinated.cc/api/ws/name-changes",
    {
      reconnectAttempts: 1000000000,
      reconnectInterval: 1000,
      retryOnError: true,
      onOpen: () => {
        if (hasConnectedRef.current) {
          void refetch();
        } else {
          hasConnectedRef.current = true;
        }
      },
    }
  );

  useEffect(() => {
    if (!lastJsonMessage) return;

    queryClient.setQueryData<RecentUsernameChange[]>(RECENT_NAME_CHANGES_QUERY_KEY, prev =>
      [lastJsonMessage, ...(prev ?? initialNameChanges)].slice(0, TOTAL_NAME_CHANGES_TO_SHOW)
    );
  }, [lastJsonMessage, queryClient, initialNameChanges]);

  return (
    <Card className="w-full max-w-[980px] overflow-hidden p-0">
      <CardHeader>Name Changes</CardHeader>
      <CardContent className="p-0">
        <div className="divide-border/60 divide-y">
          {nameChanges.map(change => (
            <div key={change.playerId} className="flex items-center gap-2 px-3 py-2">
              <SimpleLink href={`/player/${change.newUsername}`}>
                <Image
                  src={`${env.NEXT_PUBLIC_API_URL}/skins/${change.playerId}/face.png`}
                  alt={change.newUsername}
                  width={22}
                  height={22}
                  className="rounded-sm"
                />
              </SimpleLink>

              <div className="flex min-w-0 flex-1 items-center gap-1.5 text-sm">
                <span className="text-muted-foreground truncate">{change.previousUsername}</span>
                <ArrowRightIcon className="text-muted-foreground size-3 shrink-0" aria-hidden />
                <SimpleLink
                  href={`/player/${change.newUsername}`}
                  className="text-foreground font-medium hover:underline"
                >
                  {change.newUsername}
                </SimpleLink>
              </div>

              <SimpleTooltip
                display={`Name change seen on ${formatDate(new Date(change.timestamp), "Do MMMM, YYYY HH:mm a")}`}
              >
                <span className="text-muted-foreground shrink-0 text-xs">
                  <TimeAgo date={new Date(change.timestamp)} />
                </span>
              </SimpleTooltip>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
