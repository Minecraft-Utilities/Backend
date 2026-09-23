"use client";

import { truncateString } from "@/common/utils";
import { Button, buttonVariants } from "@/components/ui/button";
import { VariantProps } from "class-variance-authority";
import { CopyIcon } from "lucide-react";
import { toast } from "sonner";
import SimpleTooltip from "./simple-tooltip";

export interface CopyTextButtonProps {
  text: string;
  tooltip: string;
  variant?: VariantProps<typeof buttonVariants>["variant"];
  className?: string;
}

export default function CopyTextButton({ text, tooltip, variant = "ghost", className }: CopyTextButtonProps) {
  return (
    <SimpleTooltip display={tooltip}>
      <Button
        type="button"
        variant={variant}
        size="icon-xs"
        aria-label={tooltip}
        className={className}
        onClick={async () => {
          try {
            await navigator.clipboard.writeText(text);
            toast.success("Copied to your clipboard", {
              description: (
                <p className="text-xs break-all whitespace-pre-wrap">{truncateString(text, 100)}</p>
              ),
            });
          } catch {
            toast.error("Failed to copy");
          }
        }}
      >
        <CopyIcon className="text-input size-4" />
      </Button>
    </SimpleTooltip>
  );
}
