import { formatNumberWithCommas } from "@/common/utils";
import { Skin } from "mcutils-js-api/dist/types/player/skin/skin";
import SimpleLink from "../simple-link";
import SimpleTooltip from "../simple-tooltip";
import TimeAgo from "../time-ago";
import Card, { CardContent, CardHeader } from "../ui/card";

interface SkinDetailsProps {
  skin: Skin;
}

export default function SkinDetails({ skin }: SkinDetailsProps) {
  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Details</CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm">First Seen Using</span>
          <SimpleLink
            href={`/player/${skin.firstSeenUsing}`}
            className="border-primary/20 bg-primary/10 hover:bg-primary/20 rounded-md border px-2 py-1 font-mono text-sm transition-colors"
          >
            {skin.firstSeenUsing}
          </SimpleLink>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm">Total Owners</span>
          <span className="border-primary/20 bg-primary/10 rounded-md border px-2 py-1 font-mono text-sm">
            {formatNumberWithCommas(skin.uniqueOwners)}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm">First Seen</span>
          <SimpleTooltip display={new Date(skin.firstSeen).toLocaleString()}>
            <span className="border-primary/20 bg-primary/10 cursor-default rounded-md border px-2 py-1 font-mono text-sm">
              <TimeAgo date={new Date(skin.firstSeen)} />
            </span>
          </SimpleTooltip>
        </div>
      </CardContent>
    </Card>
  );
}
