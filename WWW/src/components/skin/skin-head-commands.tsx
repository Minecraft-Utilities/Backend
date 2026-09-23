"use client";

import { env } from "@/common/env";
import { encodeSkinTextures } from "@/common/skin";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { Skin } from "mcutils-js-api/dist/types/player/skin/skin";
import { useState } from "react";
import CopyTextButton from "../copy-text-button";
import { Input } from "../ui/input";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "../ui/select";

interface SkinHeadCommandsProps {
  skin: Skin;
}

export default function SkinHeadCommands({ skin }: SkinHeadCommandsProps) {
  const [selectedIndex, setSelectedIndex] = useState(0);

  const commands = [
    {
      version: "1.20.5",
      command: `/give @p minecraft:player_head[profile={id:[I;-1507287714,1750554916,-1225423999,352299395],properties:[{name:"textures",value:"${encodeSkinTextures(skin.textureId)}"}]},minecraft:lore=['{"text":"${env.NEXT_PUBLIC_BASE_URL}/skin/${skin.id}"}']]`,
    },
    {
      version: "1.13",
      command: `/give @p minecraft:player_head{SkullOwner:{Id:a6289d5e-6857-5924-b6f5-838114ffa983,Properties:{textures:[{Value:"${encodeSkinTextures(skin.textureId)}"}]}},display:{Lore:["{"text":"${env.NEXT_PUBLIC_BASE_URL}/skin/${skin.id}"}"]}}`,
    },
    {
      version: "1.8",
      command: `/give @p minecraft:skull 1 3 {SkullOwner:{Id:a6289d5e-6857-5924-b6f5-838114ffa983,Properties:{textures:[{Value:"${encodeSkinTextures(skin.textureId)}"}]}},display:{Lore:["${env.NEXT_PUBLIC_BASE_URL}/skin/${skin.id}"]}}`,
    },
  ];

  return (
    <Card>
      <CardHeader>Head Commands (for Command Blocks)</CardHeader>
      <CardContent className="flex flex-row">
        <Select value={selectedIndex.toString()} onValueChange={value => setSelectedIndex(Number(value))}>
          <SelectTrigger className="w-26.25 rounded-r-none">
            <SelectValue placeholder={commands[selectedIndex].version} />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {commands.map((command, index) => (
                <SelectItem key={command.version} value={index.toString()}>
                  {command.version}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>

        {/* Text input */}
        <Input
          className="rounded-l-none rounded-r-none border-x-0 text-xs break-all whitespace-pre-wrap"
          value={commands[selectedIndex].command}
          readOnly
        />

        <CopyTextButton
          text={commands[selectedIndex].command}
          tooltip="Copy Command"
          variant="outline"
          className="size-9 rounded-l-none"
        />
      </CardContent>
    </Card>
  );
}
