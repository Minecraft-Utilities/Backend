"use client";

import CountUp from "react-countup";
import useWebSocket from "react-use-websocket";
import SimpleTooltip from "../simple-tooltip";
import Card, { CardContent, CardHeader } from "../ui/card";

export default function Statistics() {
  const { lastJsonMessage } = useWebSocket("wss://mc.fascinated.cc/api/ws/statistics", {
    reconnectAttempts: 1000000000,
    reconnectInterval: 1000,
    retryOnError: true,
  });
  const statistics = lastJsonMessage as { playersTracked: number; trackedSkins: number };

  return (
    <div className="mt-8 flex w-full max-w-2xl flex-row gap-4">
      <Card className="w-full">
        <CardHeader>Stored Minecraft Profiles</CardHeader>
        <CardContent className="text-xl">
          <SimpleTooltip
            display={<p>The number of Java Minecraft profiles that have been stored in our database.</p>}
          >
            {statistics ? <CountUp end={statistics.playersTracked} preserveValue /> : "..."}
          </SimpleTooltip>
        </CardContent>
      </Card>
      <Card className="w-full">
        <CardHeader>Skin Collection Size</CardHeader>
        <CardContent className="text-xl">
          <SimpleTooltip display={<p>The number of unique skins that have been stored in our database.</p>}>
            {statistics ? <CountUp end={statistics.trackedSkins} preserveValue /> : "..."}
          </SimpleTooltip>
        </CardContent>
      </Card>
    </div>
  );
}
