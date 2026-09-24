"use client";

import { formatNumberWithCommas } from "@/common/utils";
import { ChartConfig, ChartContainer, ChartTooltip } from "@/components/ui/chart";
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { Item, ItemActions, ItemContent, ItemGroup, ItemMedia, ItemTitle } from "@/components/ui/item";
import { ChartPie } from "lucide-react";
import { Cell, Pie, PieChart } from "recharts";
import { colorFor, formatPercent } from "./chart-utils";

interface PieBreakdownProps {
  /** Raw key -> count breakdown from the API (eg: platform name, protocol number, country code). */
  data: Record<string, number>;
  /** Display name per key, precomputed by the server (eg: capitalized platform, version label, flag + country). */
  labels: Record<string, string>;
  /** Label shown under the count in the center of the donut. */
  centerLabel: string;
  /** Message shown when the breakdown is empty. */
  emptyMessage: string;
  /**
   * The total the breakdown is a subset of. When provided, the donut center reads
   * "<shown> of <grandTotal> <centerLabel>", a remainder row ("everything else") is
   * appended, and counts/percentages are measured against the grand total.
   */
  grandTotal?: number;
  /** Label of the remainder row (eg: "Other countries"). Defaults to "Other". */
  remainderLabel?: string;
}

interface BreakdownTooltipProps {
  active?: boolean;
  payload?: ReadonlyArray<{
    name?: unknown;
    value?: unknown;
    payload?: { fill?: string };
  }>;
  labels: Record<string, string>;
  unit: string;
  total: number;
}

function BreakdownTooltip({ active, payload, labels, unit, total }: BreakdownTooltipProps) {
  if (!active || !payload?.length) {
    return null;
  }

  const [item] = payload;
  const name = String(item.name ?? "");
  const count = Number(item.value ?? 0);
  const displayName = labels[name] ?? name;
  const fill = item.payload?.fill ?? colorFor(name, 0);

  return (
    <div className="border-border/50 bg-background min-w-[15rem] rounded-lg border px-2.5 py-2 text-xs shadow-xl">
      <div className="flex items-center gap-1.5 font-medium">
        <span className="size-2.5 shrink-0 rounded-full" style={{ backgroundColor: fill }} />
        <span className="whitespace-nowrap">{displayName}</span>
      </div>
      <div className="mt-1 flex items-baseline gap-1">
        <span className="text-foreground text-sm font-semibold tabular-nums">
          {formatNumberWithCommas(count)}
        </span>
        <span className="text-muted-foreground">{unit}</span>
        <span className="text-muted-foreground ml-auto tabular-nums">{formatPercent(count, total)}</span>
      </div>
    </div>
  );
}

export default function PieBreakdown({
  data,
  labels,
  centerLabel,
  emptyMessage,
  grandTotal,
  remainderLabel = "Other",
}: PieBreakdownProps) {
  const entries = Object.entries(data)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1]);
  const total = entries.reduce((sum, [, count]) => sum + count, 0);
  const displayName = (name: string) => labels[name] ?? name;

  if (entries.length === 0) {
    return (
      <Empty className="min-h-48">
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <ChartPie aria-hidden />
          </EmptyMedia>
          <EmptyTitle>No data yet</EmptyTitle>
          <EmptyDescription>{emptyMessage}</EmptyDescription>
        </EmptyHeader>
      </Empty>
    );
  }

  // Shares are measured against the grand total when the breakdown is a subset of one,
  // otherwise against the listed entries.
  const totalForPercent = grandTotal ?? total;
  const remainder = Math.max(grandTotal !== undefined ? grandTotal - total : 0, 0);
  const showRemainder = grandTotal !== undefined && remainder > 0;

  const chartData = entries.map(([name, count]) => ({ name, count }));
  const config: ChartConfig = {};
  entries.forEach(([name], index) => {
    config[name] = { label: displayName(name), color: colorFor(name, index) };
  });

  return (
    <div className="flex flex-col items-center gap-6">
      <div
        className="relative size-48 shrink-0"
        role="img"
        aria-label={`${centerLabel}: ${entries
          .map(([name, count]) => `${displayName(name)} (${formatNumberWithCommas(count)})`)
          .join(", ")}`}
      >
        <ChartContainer config={config} className="absolute inset-0 aspect-auto">
          <PieChart>
            <ChartTooltip
              wrapperStyle={{ zIndex: 10 }}
              content={({ active, payload }) => (
                <BreakdownTooltip
                  active={active}
                  payload={payload}
                  labels={labels}
                  unit={centerLabel}
                  total={totalForPercent}
                />
              )}
            />
            <Pie
              data={chartData}
              dataKey="count"
              nameKey="name"
              innerRadius={62}
              outerRadius={76}
              paddingAngle={2}
              strokeWidth={0}
            >
              {chartData.map((entry, index) => (
                <Cell key={entry.name} fill={colorFor(entry.name, index)} />
              ))}
            </Pie>
          </PieChart>
        </ChartContainer>
      </div>

      <ItemGroup className="min-w-0">
        {entries.map(([name, count], index) => (
          <Item key={name} size="xs">
            <ItemMedia>
              <span
                className="size-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: colorFor(name, index) }}
              />
            </ItemMedia>
            <ItemContent className="min-w-0">
              <ItemTitle>{displayName(name)}</ItemTitle>
            </ItemContent>
            <ItemActions>
              <span className="tabular-nums">{formatNumberWithCommas(count)}</span>
              <span className="text-muted-foreground w-12 shrink-0 text-right text-xs tabular-nums">
                {formatPercent(count, totalForPercent)}
              </span>
            </ItemActions>
          </Item>
        ))}

        {showRemainder && (
          <Item size="xs">
            <ItemMedia>
              <span className="bg-muted size-2.5 shrink-0 rounded-full" />
            </ItemMedia>
            <ItemContent className="min-w-0">
              <ItemTitle className="text-muted-foreground font-normal">{remainderLabel}</ItemTitle>
            </ItemContent>
            <ItemActions>
              <span className="tabular-nums">{formatNumberWithCommas(remainder)}</span>
              <span className="text-muted-foreground w-12 shrink-0 text-right text-xs tabular-nums">
                {formatPercent(remainder, totalForPercent)}
              </span>
            </ItemActions>
          </Item>
        )}
      </ItemGroup>
    </div>
  );
}
