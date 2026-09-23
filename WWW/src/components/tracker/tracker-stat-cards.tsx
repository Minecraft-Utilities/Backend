import type { TrackerStats } from "@/common/tracker";
import { formatNumberWithCommas } from "@/common/utils";
import Card, { CardContent } from "@/components/ui/card";

interface TrackerStatCardsProps {
  stats: TrackerStats;
}

export default function TrackerStatCards({ stats }: TrackerStatCardsProps) {
  const definitions = [
    {
      label: "Tracked servers",
      value: stats.trackedServers,
      description: "currently tracked",
    },
    {
      label: "Tracked players",
      value: stats.trackedPlayers,
      description: "unique players observed",
    },
    {
      label: "Online players",
      value: stats.onlinePlayers,
      description: "present in latest samples",
    },
  ];

  return (
    <Card className="w-full overflow-hidden p-0">
      <CardContent className="divide-border/60 grid grid-cols-1 divide-y p-0 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
        {definitions.map(definition => (
          <div key={definition.label} className="flex flex-col gap-1 px-5 py-5">
            <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">
              {definition.label}
            </p>
            <p className="text-foreground text-3xl font-semibold tracking-tight tabular-nums">
              {formatNumberWithCommas(definition.value)}
            </p>
            <p className="text-muted-foreground text-xs">{definition.description}</p>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
