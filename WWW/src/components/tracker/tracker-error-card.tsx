import Card, { CardContent, CardHeader } from "@/components/ui/card";
import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";

export interface TrackerErrorCardProps {
  title: string;
  message: string;
  action?: ReactNode;
}

export default function TrackerErrorCard({ title, message, action }: TrackerErrorCardProps) {
  return (
    <Card className="border-destructive/40 bg-destructive/10 w-full max-w-2xl overflow-hidden p-0">
      <CardHeader variant="destructive">{title}</CardHeader>
      <CardContent className="flex flex-col items-start gap-4 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <CircleAlert className="text-destructive mt-0.5 size-5 shrink-0" aria-hidden />
          <p className="text-muted-foreground text-sm leading-relaxed">{message}</p>
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </CardContent>
    </Card>
  );
}
