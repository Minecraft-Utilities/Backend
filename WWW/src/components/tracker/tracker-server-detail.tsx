import { serverAddress, type TrackedServerDetail } from "@/common/tracker";
import { formatNumberWithCommas } from "@/common/utils";
import { ProfileCopyableValue, ProfileField, ProfileFields, ProfileValue } from "@/components/profile-field";
import TimeAgo from "@/components/time-ago";
import { countryFlag, countryLabel } from "@/components/tracker/chart-utils";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { Item, ItemContent, ItemDescription, ItemMedia, ItemTitle } from "@/components/ui/item";
import { Separator } from "@/components/ui/separator";
import { Check, CircleAlert, Clock3, MapPin, ShieldCheck, Users, Wifi, X } from "lucide-react";
import type { ReactNode } from "react";

export interface TrackerServerDetailProps {
  server: TrackedServerDetail;
}

function FreshnessValue({
  value,
  unavailable = "Not reported",
}: {
  value?: string | null;
  unavailable?: string;
}) {
  const date = value ? new Date(value) : null;
  if (!date || Number.isNaN(date.getTime()) || !value) {
    return <span className="text-muted-foreground">{unavailable}</span>;
  }

  return (
    <time dateTime={value} title={date.toLocaleString()} className="inline-flex items-center gap-1.5">
      <TimeAgo date={date} />
    </time>
  );
}

function OptionalValue({
  value,
  unavailable = "Not reported",
}: {
  value?: string | number | null;
  unavailable?: string;
}) {
  return <ProfileValue>{value == null || value === "" ? unavailable : value}</ProfileValue>;
}

function DetailCard({
  title,
  children,
  className,
}: {
  title: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Card className={`h-full overflow-hidden p-0 ${className ?? ""}`}>
      <CardHeader>{title}</CardHeader>
      <CardContent className="p-4 pt-3">{children}</CardContent>
    </Card>
  );
}

export function TrackerServerDetail({ server }: TrackerServerDetailProps) {
  const address = serverAddress(server);
  const country = server.country?.trim();
  const flag = country ? countryFlag(country) : null;
  const motd = server.motd?.trim();
  const capabilities = [
    {
      enabled: server.modded,
      label: server.modded ? "Modded server detected" : "No modded server detected",
    },
    {
      enabled: server.preventsChatReports,
      label: server.preventsChatReports ? "Prevents chat reports" : "Does not prevent chat reports",
    },
    {
      enabled: server.enforcesSecureChat,
      label: server.enforcesSecureChat ? "Enforces secure chat" : "Does not enforce secure chat",
    },
    {
      enabled: server.previewsChat,
      label: server.previewsChat ? "Previews chat" : "Does not preview chat",
    },
  ];

  return (
    <section className="flex w-full min-w-0 flex-col gap-5" aria-label="Tracked server telemetry">
      <Card className="overflow-hidden p-0">
        <CardContent className="p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-3">
              <span
                className={
                  server.online
                    ? "size-2.5 rounded-full bg-emerald-500"
                    : "bg-muted-foreground/50 size-2.5 rounded-full"
                }
                aria-hidden
              />
              <div>
                <p className="text-foreground font-medium">{server.online ? "Online" : "Offline"}</p>
                <p className="text-muted-foreground text-xs">Latest tracker refresh result</p>
              </div>
            </div>
            <div className="text-muted-foreground flex items-center gap-1.5 text-xs">
              <Clock3 className="size-3.5 shrink-0" aria-hidden />
              Last checked <FreshnessValue value={server.lastCheckedAt} />
            </div>
          </div>

          <Separator className="my-4" />

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Item variant="muted" size="sm">
              <ItemMedia variant="icon">
                <Users aria-hidden />
              </ItemMedia>
              <ItemContent>
                <ItemTitle className="tabular-nums">
                  {formatNumberWithCommas(server.onlineCount)} / {formatNumberWithCommas(server.maxPlayers)}
                </ItemTitle>
                <ItemDescription>advertised players</ItemDescription>
              </ItemContent>
            </Item>

            <Item variant="muted" size="sm">
              <ItemMedia variant="icon">
                <Wifi aria-hidden />
              </ItemMedia>
              <ItemContent>
                <ItemTitle className="truncate">{server.version ?? "Unknown"}</ItemTitle>
                <ItemDescription className="truncate">
                  {server.platform ?? "software unknown"}
                </ItemDescription>
              </ItemContent>
            </Item>

            <Item variant="muted" size="sm">
              <ItemMedia variant="icon">
                <MapPin aria-hidden />
              </ItemMedia>
              <ItemContent>
                <ItemTitle className="truncate">
                  {country ? (
                    <>
                      {flag ? <span aria-hidden>{flag}</span> : null}
                      {countryLabel(country)}
                    </>
                  ) : (
                    "Unknown"
                  )}
                </ItemTitle>
                <ItemDescription>estimated location</ItemDescription>
              </ItemContent>
            </Item>

            <Item variant="muted" size="sm">
              <ItemMedia variant="icon">
                <Clock3 aria-hidden />
              </ItemMedia>
              <ItemContent>
                <ItemTitle className="tabular-nums">
                  {server.latencyMs == null ? "—" : `${server.latencyMs} ms`}
                </ItemTitle>
                <ItemDescription>observed latency</ItemDescription>
              </ItemContent>
            </Item>
          </div>
        </CardContent>
      </Card>

      <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2">
        <DetailCard title="Telemetry">
          <ProfileFields>
            <ProfileField label="Version">
              <OptionalValue value={server.version} />
            </ProfileField>
            <ProfileField label="Platform">
              <OptionalValue value={server.platform} />
            </ProfileField>
            <ProfileField label="First seen">
              <FreshnessValue value={server.firstSeen} />
            </ProfileField>
            <ProfileField label="Last seen">
              <FreshnessValue value={server.lastUpdated} />
            </ProfileField>
          </ProfileFields>
        </DetailCard>

        <DetailCard title="Network">
          <ProfileFields>
            <ProfileField label="Address">
              <ProfileCopyableValue text={address} />
            </ProfileField>
            <ProfileField label="Country">
              <OptionalValue value={country ? countryLabel(country) : null} />
            </ProfileField>
            <ProfileField label="ASN" tooltip="Autonomous-system number associated with the server address.">
              <OptionalValue value={server.asn == null ? null : `AS${server.asn}`} />
            </ProfileField>
          </ProfileFields>
        </DetailCard>

        <DetailCard title="MOTD" className="lg:col-span-2">
          {motd ? (
            <p className="text-foreground/80 text-sm leading-relaxed break-words whitespace-pre-wrap">
              {motd}
            </p>
          ) : (
            <Empty className="py-6">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <CircleAlert aria-hidden />
                </EmptyMedia>
                <EmptyTitle>No MOTD reported</EmptyTitle>
                <EmptyDescription>The server did not advertise a message of the day.</EmptyDescription>
              </EmptyHeader>
            </Empty>
          )}
        </DetailCard>

        <DetailCard title="Server capabilities" className="lg:col-span-2">
          <div className="grid gap-3 sm:grid-cols-2">
            {capabilities.map(capability => (
              <Item key={capability.label} variant="outline" size="sm">
                <ItemMedia variant="icon">
                  {capability.enabled ? (
                    <Check className="text-emerald-500" aria-hidden />
                  ) : (
                    <X className="text-muted-foreground" aria-hidden />
                  )}
                </ItemMedia>
                <ItemContent>
                  <ItemTitle className={capability.enabled ? undefined : "text-muted-foreground font-normal"}>
                    {capability.label}
                  </ItemTitle>
                </ItemContent>
              </Item>
            ))}
          </div>
          <p className="text-muted-foreground mt-4 flex items-start gap-2 text-xs">
            <ShieldCheck className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            Capability flags are advertised by the server and are not independently verified.
          </p>
        </DetailCard>
      </div>
    </section>
  );
}

export default TrackerServerDetail;
