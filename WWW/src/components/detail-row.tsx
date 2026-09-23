import { cn } from "@/common/utils";
import { Info } from "lucide-react";
import { ReactNode } from "react";
import CopyTextButton from "./copy-text-button";
import SimpleTooltip from "./simple-tooltip";

const variantClasses = {
  default: "rounded-md border border-primary/20 bg-primary/10 px-2 py-1 font-mono text-sm text-foreground",
  success:
    "rounded-md border border-emerald-500/30 bg-emerald-500/10 px-2 py-1 text-sm text-emerald-600 dark:text-emerald-400",
  warning:
    "rounded-md border border-amber-500/30 bg-amber-500/10 px-2 py-1 text-sm text-amber-600 dark:text-amber-400",
} as const;

export type DetailRowVariant = keyof typeof variantClasses;

export interface DetailRowProps {
  label: string;
  value: ReactNode;
  copyText?: string;
  variant?: DetailRowVariant;
  copyable?: boolean;
  tooltip?: string;
}

export default function DetailRow({
  label,
  value,
  copyText,
  variant = "default",
  copyable = false,
  tooltip,
}: DetailRowProps) {
  return (
    <div className="xs:flex-row xs:flex-wrap xs:items-center xs:justify-between xs:gap-2 flex flex-col gap-1.5 py-1.5 first:pt-0 last:pb-0">
      <div className="flex items-center gap-1.5">
        <span className="text-muted-foreground shrink-0 text-sm select-none">{label}</span>
        {tooltip && (
          <SimpleTooltip display={tooltip} side="right">
            <Info className="text-muted-foreground/60 hover:text-muted-foreground size-3.5 shrink-0 cursor-default transition-colors" />
          </SimpleTooltip>
        )}
      </div>

      <div className="flex items-center gap-2">
        {copyable && <CopyTextButton text={copyText ?? (value as string)} tooltip="Copy to clipboard" />}
        <span className={cn("min-w-0 break-all", variantClasses[variant])}>{value}</span>
      </div>
    </div>
  );
}
