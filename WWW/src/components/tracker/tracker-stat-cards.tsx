import type { TrackerStats } from "@/common/tracker";
import { formatNumberWithCommas } from "@/common/utils";
import Card, { CardContent } from "@/components/ui/card";
import { Item, ItemContent, ItemDescription, ItemGroup, ItemTitle } from "@/components/ui/item";

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
    <Card className="w-full">
      <CardContent>
        <ItemGroup className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {definitions.map(definition => (
            <Item key={definition.label} variant="muted" size="sm">
              <ItemContent>
                <ItemTitle>{definition.label}</ItemTitle>
                <p className="text-3xl font-semibold tracking-tight tabular-nums">
                  {formatNumberWithCommas(definition.value)}
                </p>
                <ItemDescription>{definition.description}</ItemDescription>
              </ItemContent>
            </Item>
          ))}
        </ItemGroup>
      </CardContent>
    </Card>
  );
}
