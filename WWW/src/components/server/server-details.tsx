import { BedrockServer } from "mcutils-js-api/dist/types/server/impl/bedrock-server";
import { JavaServer } from "mcutils-js-api/dist/types/server/impl/java-server";
import { Server, ServerPlatform } from "mcutils-js-api/dist/types/server/server";
import { ProfileCopyableValue, ProfileField, ProfileFields, ProfileValue } from "@/components/profile-field";
import TimeAgo from "../time-ago";
import Card, { CardContent, CardHeader } from "../ui/card";

export interface ServerDetailsProps {
  server: Server;
  edition: ServerPlatform;
}

export function ServerDetails({ server, edition }: ServerDetailsProps) {
  const javaServer = server as JavaServer;
  const bedrockServer = server as BedrockServer;

  return (
    <section className="flex flex-col gap-4">
      <Card className="w-full min-w-0 overflow-hidden p-0">
        <CardHeader>Details</CardHeader>
        <CardContent className="p-3 pt-2">
          <ProfileFields>
          <ProfileField label="Cached">
            <ProfileValue>
              {server.cached ? <TimeAgo date={new Date(server.cachedTime)} /> : "No"}
            </ProfileValue>
          </ProfileField>

          <ProfileField label="Hostname">
            <ProfileCopyableValue text={server.hostname} />
          </ProfileField>

          <ProfileField label="IP address">
            <ProfileCopyableValue text={server.ip} />
          </ProfileField>

          <ProfileField label="Port">
            <ProfileValue>{String(server.port)}</ProfileValue>
          </ProfileField>

          {edition === "java" && (
            <>
              {javaServer.version && (
                <ProfileField label="Protocol version">
                  <ProfileValue>
                    {`${javaServer.version.protocolName ?? javaServer.version.name} (${javaServer.version.protocol})`}
                  </ProfileValue>
                </ProfileField>
              )}
              <ProfileField label="Blocked by Mojang">
                <ProfileValue>{javaServer.mojangBlocked ? "Yes" : "No"}</ProfileValue>
              </ProfileField>
              {javaServer.isModded && (
                <ProfileField label="Modded server">
                  <ProfileValue>{`${javaServer.forgeData?.mods?.length ?? 0} mods`}</ProfileValue>
                </ProfileField>
              )}
              <ProfileField label="Prevents chat reports">
                <ProfileValue>{javaServer.preventsChatReports ? "Yes" : "No"}</ProfileValue>
              </ProfileField>
              <ProfileField label="Enforces secure chat">
                <ProfileValue>{javaServer.enforcesSecureChat ? "Yes" : "No"}</ProfileValue>
              </ProfileField>
              <ProfileField
                label="Previews chat"
                tooltip="When enabled, the server receives chat messages as they are typed and sends back a styled preview. This allows custom formatting like emojis or chat colors, but messages are transmitted to the server before being sent."
              >
                <ProfileValue>{javaServer.previewsChat ? "Yes" : "No"}</ProfileValue>
              </ProfileField>
            </>
          )}

          {edition === "bedrock" && (
            <>
              {bedrockServer.version?.name && (
                <ProfileField label="Version">
                  <ProfileValue>{bedrockServer.version.name}</ProfileValue>
                </ProfileField>
              )}
              {bedrockServer.gamemode?.name && (
                <ProfileField label="Gamemode">
                  <ProfileValue>{bedrockServer.gamemode.name}</ProfileValue>
                </ProfileField>
              )}
            </>
          )}

          <ProfileField label="SRV record">
            <ProfileValue>{server.records?.some(r => r.type === "SRV") ? "Yes" : "No"}</ProfileValue>
          </ProfileField>

          {server.location?.country && (
            <ProfileField label="Location">
              <ProfileValue>{server.location.country}</ProfileValue>
            </ProfileField>
          )}
          </ProfileFields>
        </CardContent>
      </Card>
    </section>
  );
}
