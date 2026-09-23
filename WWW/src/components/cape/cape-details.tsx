import { Cape } from "mcutils-js-api/dist/types/player/cape/cape";
import SimpleLink from "../simple-link";
import SimpleTooltip from "../simple-tooltip";
import TimeAgo from "../time-ago";
import Card, { CardContent, CardHeader } from "../ui/card";

interface CapeDetailsProps {
  cape: Cape;
}

export default function CapeDetails({ cape }: CapeDetailsProps) {
  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Details</CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm">First Seen Owning</span>
          <SimpleLink
            href={`/player/${cape.firstSeenUsing}`}
            className="border-primary/20 bg-primary/10 hover:bg-primary/20 rounded-md border px-2 py-1 font-mono text-sm transition-colors"
          >
            {cape.firstSeenUsing}
          </SimpleLink>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm">First Seen</span>
          <SimpleTooltip display={new Date(cape.firstSeen).toLocaleString()}>
            <span className="border-primary/20 bg-primary/10 cursor-default rounded-md border px-2 py-1 font-mono text-sm">
              <TimeAgo date={new Date(cape.firstSeen)} />
            </span>
          </SimpleTooltip>
        </div>
      </CardContent>
    </Card>
  );
}
