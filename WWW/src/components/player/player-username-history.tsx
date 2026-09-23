import { formatDate } from "@/common/time-utils";
import CopyLink from "@/components/copy-link";
import SimpleTooltip from "@/components/simple-tooltip";
import TimeAgo from "@/components/time-ago";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import type { FullPlayer } from "mcutils-js-api/dist/types/player/player";

export interface PlayerUsernameHistoryProps {
  player: FullPlayer;
}

export default function PlayerUsernameHistory({ player }: PlayerUsernameHistoryProps) {
  const history = player.usernameHistory;

  if (!history || history.length === 0) return null;

  const sorted = [...history].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  );

  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Name History</CardHeader>
      <CardContent className="p-0">
        <div className="divide-border/60 divide-y">
          {sorted.map((entry, i) => (
            <div key={i} className="flex items-center gap-2 px-3 py-1.5 text-sm">
              <span className="text-muted-foreground w-5 shrink-0 text-right text-xs">
                {sorted.length - i}.
              </span>
              <span className="text-foreground min-w-0 flex-1 truncate font-medium">{entry.newUsername}</span>
              {i !== sorted.length - 1 && (
                <SimpleTooltip
                  display={`Name change seen on ${formatDate(new Date(entry.timestamp), "Do MMMM, YYYY HH:mm a")}`}
                >
                  <span className="text-muted-foreground shrink-0 text-xs">
                    <TimeAgo date={new Date(entry.timestamp)} />
                  </span>
                </SimpleTooltip>
              )}
              <CopyLink text={entry.newUsername} />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
