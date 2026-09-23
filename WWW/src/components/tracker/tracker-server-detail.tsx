import type { TrackedServerDetail } from "@/common/tracker";
import { formatNumberWithCommas } from "@/common/utils";
import { ProfileCopyableValue, ProfileField, ProfileFields, ProfileValue } from "@/components/profile-field";
import TimeAgo from "@/components/time-ago";
import { countryFlag, countryLabel, protocolLabel } from "@/components/tracker/chart-utils";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
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

function CapabilityValue({ enabled, label }: { enabled: boolean; label: string }) {
  return (
    <span
      className={
        enabled
          ? "inline-flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400"
          : "text-muted-foreground inline-flex items-center gap-1.5"
      }
    >
      {enabled ? <Check className="size-3.5" aria-hidden /> : <X className="size-3.5" aria-hidden />}
      {label}
    </span>
  );
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
  const address =
    server.ip.includes(":") && !server.ip.startsWith("[")
      ? `[${server.ip}]:${server.port}`
      : `${server.ip}:${server.port}`;
  const country = server.country?.trim();
  const flag = country ? countryFlag(country) : null;
  const motd = server.motd?.trim();

  return (
    <section className="flex w-full min-w-0 flex-col gap-5" aria-label="Tracked server telemetry">
      <div className="border-border/70 bg-card/70 rounded-xl border p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <span
              className={
                server.online
                  ? "size-2.5 rounded-full bg-emerald-500 shadow-[0_0_0_4px_rgb(16_185_129_/_0.12)]"
                  : "bg-muted-foreground/50 size-2.5 rounded-full"
              }
              aria-hidden
            />
            <div>
              <p className="text-foreground font-semibold">{server.online ? "Online" : "Offline"}</p>
              <p className="text-muted-foreground text-xs">Latest tracker refresh result</p>
            </div>
          </div>
          <div className="text-muted-foreground flex items-center gap-2 text-xs">
            <Clock3 className="size-3.5" aria-hidden />
            Last checked <FreshnessValue value={server.lastCheckedAt} />
          </div>
        </div>

        <div className="border-border/60 md:divide-border/60 mt-5 grid grid-cols-2 gap-4 border-t pt-5 md:grid-cols-4 md:divide-x">
          <div className="flex items-start gap-2 md:px-4 md:first:pl-0">
            <Users className="text-muted-foreground mt-0.5 size-4 shrink-0" aria-hidden />
            <div>
              <p className="text-foreground font-semibold tabular-nums">
                {formatNumberWithCommas(server.onlineCount)} / {formatNumberWithCommas(server.maxPlayers)}
              </p>
              <p className="text-muted-foreground text-xs">advertised players</p>
            </div>
          </div>
          <div className="flex items-start gap-2 md:px-4">
            <Wifi className="text-muted-foreground mt-0.5 size-4 shrink-0" aria-hidden />
            <div className="min-w-0">
              <p className="text-foreground truncate font-semibold">{server.version ?? "Unknown"}</p>
              <p className="text-muted-foreground text-xs">{server.platform ?? "software unknown"}</p>
            </div>
          </div>
          <div className="flex items-start gap-2 md:px-4">
            <MapPin className="text-muted-foreground mt-0.5 size-4 shrink-0" aria-hidden />
            <div className="min-w-0">
              <p className="text-foreground truncate font-semibold">
                {country ? (
                  <>
                    {flag ? <span aria-hidden>{flag} </span> : null}
                    {countryLabel(country)}
                  </>
                ) : (
                  "Unknown"
                )}
              </p>
              <p className="text-muted-foreground text-xs">estimated location</p>
            </div>
          </div>
          <div className="flex items-start gap-2 md:px-4">
            <Clock3 className="text-muted-foreground mt-0.5 size-4 shrink-0" aria-hidden />
            <div>
              <p className="text-foreground font-semibold">
                {server.latencyMs == null ? "—" : `${server.latencyMs} ms`}
              </p>
              <p className="text-muted-foreground text-xs">observed latency</p>
            </div>
          </div>
        </div>
      </div>

      <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2">
        <DetailCard title="Telemetry">
          <ProfileFields>
            <ProfileField label="Version">
              <OptionalValue value={server.version} />
            </ProfileField>
            <ProfileField label="Platform">
              <OptionalValue value={server.platform} />
            </ProfileField>
            <ProfileField label="Protocol">
              <OptionalValue
                value={server.protocol == null ? null : protocolLabel(String(server.protocol))}
              />
            </ProfileField>
            <ProfileField label="First seen">
              <FreshnessValue value={server.firstSeen} />
            </ProfileField>
            <ProfileField label="Last seen">
              <FreshnessValue value={server.lastUpdated} />
            </ProfileField>
            <ProfileField label="Failed checks" tooltip="Consecutive refresh attempts that did not succeed.">
              <ProfileValue>{formatNumberWithCommas(server.consecutiveOffline)}</ProfileValue>
            </ProfileField>
          </ProfileFields>
        </DetailCard>

        <DetailCard title="Network">
          <ProfileFields>
            <ProfileField label="UUID">
              <ProfileCopyableValue text={server.uuid} />
            </ProfileField>
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
            <p className="text-muted-foreground flex items-center gap-2 text-sm">
              <CircleAlert className="size-4" aria-hidden />
              No MOTD was reported for this server.
            </p>
          )}
        </DetailCard>

        <DetailCard title="Server capabilities" className="lg:col-span-2">
          <div className="grid gap-3 sm:grid-cols-2">
            <CapabilityValue
              enabled={server.modded}
              label={server.modded ? "Modded server detected" : "No modded server detected"}
            />
            <CapabilityValue
              enabled={server.preventsChatReports}
              label={server.preventsChatReports ? "Prevents chat reports" : "Does not prevent chat reports"}
            />
            <CapabilityValue
              enabled={server.enforcesSecureChat}
              label={server.enforcesSecureChat ? "Enforces secure chat" : "Does not enforce secure chat"}
            />
            <CapabilityValue
              enabled={server.previewsChat}
              label={server.previewsChat ? "Previews chat" : "Does not preview chat"}
            />
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
