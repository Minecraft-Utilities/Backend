import { JavaServer } from "mcutils-js-api/dist/types/server/impl/java-server";
import { Server, ServerPlatform } from "mcutils-js-api/dist/types/server/server";
import Image from "next/image";
import Card, { CardContent } from "../ui/card";

interface ServerMotdProps {
  server: Server;
  edition: ServerPlatform;
}

export default function ServerMotd({ server, edition }: ServerMotdProps) {
  return (
    edition === "java" &&
    (server as JavaServer).motd?.preview && (
      <section className="flex flex-col gap-4">
        <Card className="h-fit items-center overflow-hidden p-0">
          <CardContent className="flex items-center justify-center">
            <Image
              src={(server as JavaServer).motd!.preview!}
              alt={`${server.hostname} MOTD preview`}
              width={768}
              height={87}
              className="w-auto rounded-md object-contain"
              priority
            />
          </CardContent>
        </Card>
      </section>
    )
  );
}
