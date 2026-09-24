import { Alert, AlertAction, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";

export interface TrackerErrorCardProps {
  title: string;
  message: string;
  action?: ReactNode;
}

export default function TrackerErrorCard({ title, message, action }: TrackerErrorCardProps) {
  return (
    <Alert variant="destructive" className="w-full max-w-2xl">
      <CircleAlert aria-hidden />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>{message}</AlertDescription>
      {action ? <AlertAction className="text-foreground">{action}</AlertAction> : null}
    </Alert>
  );
}
