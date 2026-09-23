import { DownloadIcon } from "lucide-react";
import { Cape } from "mcutils-js-api/dist/types/player/cape/cape";
import Card, { CardContent } from "../ui/card";
import DownloadFileButton from "../ui/download-file-button";

interface CapePreviewProps {
  cape: Cape;
}

export default function CapePreview({ cape }: CapePreviewProps) {
  return (
    <Card className="h-105 w-full">
      <CardContent className="group relative flex items-center justify-center p-4">
        <img
          src={`https://mc.fascinated.cc/api/skins/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b/fullbody_iso_back.png?capeId=${cape.id}`}
          alt={cape.name ?? cape.id}
          className="h-96"
        />
        <div className="absolute top-2 right-2 opacity-0 transition-opacity group-hover:opacity-100">
          <DownloadFileButton href={cape.textureUrl} filename={`${cape.textureId}.png`}>
            <DownloadIcon className="size-4" />
          </DownloadFileButton>
        </div>
      </CardContent>
    </Card>
  );
}
