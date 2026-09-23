import { FullPlayer } from "mcutils-js-api/dist/types/player/player";
import Image from "next/image";

export interface PlayerHeaderProps {
  player: FullPlayer;
}

export default function PlayerHeader({ player }: PlayerHeaderProps) {
  return (
    <header className="w-full text-left">
      <div className="flex items-center gap-3 sm:gap-4">
        <Image
          src={player.skin.parts.HEAD_ISO}
          alt=""
          width={56}
          height={56}
          className="shrink-0"
          priority
        />
        <div className="min-w-0">
          <h1 className="text-foreground text-4xl font-bold tracking-tight">{player.username}</h1>
          <p className="text-muted-foreground mt-2 text-sm">Skin, capes, and profile history.</p>
        </div>
      </div>
    </header>
  );
}
