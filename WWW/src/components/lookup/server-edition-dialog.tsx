"use client";

import { cn } from "@/common/utils";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Coffee, Layers } from "lucide-react";
import { ServerPlatform } from "mcutils-js-api/dist/types/server/server";

export interface ServerEditionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  serverAddress: string;
  onSelectEdition: (edition: ServerPlatform) => void;
}

const editionOptions: {
  type: ServerPlatform;
  label: string;
  icon: typeof Coffee;
  description: string;
}[] = [
  { type: "java", label: "Java", icon: Coffee, description: "PC, Mac, Linux" },
  {
    type: "bedrock",
    label: "Bedrock",
    icon: Layers,
    description: "Console, Mobile, Windows 10",
  },
];

export default function ServerEditionDialog({
  open,
  onOpenChange,
  serverAddress,
  onSelectEdition,
}: ServerEditionDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent showCloseButton={true} className="sm:max-w-md">
        <DialogHeader className="space-y-3">
          <DialogTitle className="text-xl">Choose server edition</DialogTitle>
          <DialogDescription className="leading-relaxed">
            Which edition is{" "}
            <span className="text-foreground bg-accent/50 border-border rounded border px-1.5 py-0.5 font-mono font-medium">
              {serverAddress}
            </span>{" "}
            running?
          </DialogDescription>
        </DialogHeader>
        <div className="grid grid-cols-2 gap-3 pt-2">
          {editionOptions.map(({ type, label, icon: Icon, description }) => (
            <button
              key={type}
              type="button"
              onClick={() => onSelectEdition(type)}
              className={cn(
                "group border-border bg-card/50 flex flex-col items-center gap-2 rounded-lg border-2 p-4 text-center transition-all",
                "hover:border-primary/50 hover:bg-accent/30 hover:shadow-md",
                "focus-visible:ring-ring focus-visible:ring-offset-background focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none"
              )}
            >
              <span className="bg-accent/50 text-muted-foreground group-hover:bg-primary/20 group-hover:text-primary flex size-10 items-center justify-center rounded-lg transition-colors">
                <Icon className="size-5" strokeWidth={2} />
              </span>
              <span className="text-foreground font-semibold">{label}</span>
              <span className="text-muted-foreground text-xs">{description}</span>
            </button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
