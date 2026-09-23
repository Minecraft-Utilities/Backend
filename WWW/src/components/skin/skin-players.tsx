import { formatNumberWithCommas } from "@/common/utils";
import { Skin } from "mcutils-js-api/dist/types/player/skin/skin";
import OwnerCount from "../owner-count";
import PlayerPreview from "../player/player-preview";
import SimpleLink from "../simple-link";
import Card, { CardContent, CardHeader } from "../ui/card";

interface SkinPlayersProps {
  skin: Skin;
}

export default function SkinPlayers({ skin }: SkinPlayersProps) {
  return (
    <Card className="h-105 w-full text-sm">
      <CardHeader>
        <OwnerCount count={skin.uniqueOwners} name="Owners" />
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2 overflow-y-auto">
        {skin.accountsSeenUsing?.map((account, index, accounts) => (
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
        {skin.accountsSeenUsing && skin.accountsSeenUsing.length < skin.uniqueOwners && (
          <p className="text-muted-foreground">
            + {formatNumberWithCommas(skin.uniqueOwners - skin.accountsSeenUsing.length)} more...
          </p>
        )}
      </CardContent>
    </Card>
  );
}
