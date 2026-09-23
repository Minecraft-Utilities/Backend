import { formatNumberWithCommas } from "@/common/utils";
import { ProfileCopyableValue, ProfileField, ProfileFields, ProfileValue } from "@/components/profile-field";
import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { FullPlayer } from "mcutils-js-api/dist/types/player/player";

export interface PlayerDetailsProps {
  player: FullPlayer;
}

export default function PlayerDetails({ player }: PlayerDetailsProps) {
  return (
    <Card className="h-fit w-full overflow-hidden p-0">
      <CardHeader>Details</CardHeader>
      <CardContent className="p-3 pt-2">
        <ProfileFields>
          <ProfileField label="UUID">
            <ProfileCopyableValue text={player.uniqueId} />
          </ProfileField>

          <ProfileField label="Views">
            <ProfileValue>{formatNumberWithCommas(player.monthlyViews)} / month</ProfileValue>
          </ProfileField>

          <ProfileField label="Submitted UUIDs">
            <ProfileValue>{formatNumberWithCommas(player.submittedUuids)}</ProfileValue>
          </ProfileField>
        </ProfileFields>
      </CardContent>
    </Card>
  );
}
