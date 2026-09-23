import Image from "next/image";
import SimpleLink from "../simple-link";
import SimpleTooltip from "../simple-tooltip";
import Card, { CardContent, CardFooter } from "../ui/card";

export interface LandingExampleCardProps {
  url: string;
  href: string;
  tooltip: React.ReactNode;
  name: string;
}

export function LandingExampleCard({ href, url, tooltip, name }: LandingExampleCardProps) {
  return (
    <SimpleTooltip display={tooltip}>
      <SimpleLink
        href={href}
        className="block transition-transform duration-200 hover:scale-[1.02] active:scale-[0.98]"
      >
        <Card className="size-34 p-0">
          <CardContent className="flex flex-1 items-center justify-center p-0">
            <Image alt={name} src={url} width={64} height={64} className="rounded-lg object-cover" />
          </CardContent>
          <CardFooter>
            <p className="text-muted-foreground text-center text-xs">{name}</p>
          </CardFooter>
        </Card>
      </SimpleLink>
    </SimpleTooltip>
  );
}
