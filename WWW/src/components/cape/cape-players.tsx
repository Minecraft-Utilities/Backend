import { formatNumberWithCommas } from "@/common/utils";
import { Cape } from "mcutils-js-api/dist/types/player/cape/cape";
import OwnerCount from "../owner-count";
import PlayerPreview from "../player/player-preview";
import SimpleLink from "../simple-link";
import Card, { CardContent, CardHeader } from "../ui/card";

interface CapePlayersProps {
  cape: Cape;
}

export default function CapePlayers({ cape }: CapePlayersProps) {
  return (
    <Card className="h-105 w-full text-sm">
      <CardHeader>
        <OwnerCount count={cape.uniqueOwners} name="Owners" />
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2 overflow-y-auto">
        {cape.accountsSeenOwning?.map((account, index, accounts) => (
          <div key={account} className="flex items-center">
            <PlayerPreview
              nameOrUuid={account}
              trigger={
                <SimpleLink href={`/player/${account}`}>
                  <p>{account}</p>
                </SimpleLink>
              }
            />
            {index !== accounts.length - 1 && <p className="text-muted-foreground">,</p>}
          </div>
        ))}
        {cape.accountsSeenOwning && cape.accountsSeenOwning.length < cape.uniqueOwners && (
          <p className="text-muted-foreground">
            + {formatNumberWithCommas(cape.uniqueOwners - cape.accountsSeenOwning.length)} more...
          </p>
        )}
      </CardContent>
    </Card>
  );
}
