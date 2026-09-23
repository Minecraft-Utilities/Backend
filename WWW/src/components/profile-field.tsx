import { cn } from "@/common/utils";
import CopyLink from "@/components/copy-link";
import SimpleTooltip from "@/components/simple-tooltip";
import { Info } from "lucide-react";
import { ReactNode } from "react";

export interface ProfileFieldsProps {
  children: ReactNode;
  className?: string;
}

/** Wraps profile rows in a shared two-column grid so values align across rows. */
export function ProfileFields({ children, className }: ProfileFieldsProps) {
  return (
    <div
      className={cn("grid grid-cols-[max-content_minmax(0,1fr)] gap-x-6 px-1 text-sm", className)}
    >
      {children}
    </div>
  );
}

export interface ProfileFieldProps {
  label: string;
  children: ReactNode;
  className?: string;
  tooltip?: string;
}

export function ProfileField({ label, children, className, tooltip }: ProfileFieldProps) {
  return (
    <div
      className={cn(
        "col-span-2 grid grid-cols-subgrid items-center py-2 first:pt-0 last:pb-0",
        className
      )}
    >
      <div className="flex items-center gap-1.5">
        <span className="text-foreground font-semibold">{label}</span>
        {tooltip && (
          <SimpleTooltip display={tooltip} side="right">
            <Info className="text-muted-foreground/60 hover:text-muted-foreground size-3.5 shrink-0 cursor-default transition-colors" />
          </SimpleTooltip>
        )}
      </div>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

export function ProfileValue({ children }: { children: ReactNode }) {
  return <span className="text-foreground/75 font-normal">{children}</span>;
}

export function ProfileCopyableValue({ text }: { text: string }) {
  return (
    <div className="flex min-w-0 w-full items-center gap-1.5">
      <span className="text-foreground/75 min-w-0 flex-1 truncate font-normal">{text}</span>
      <CopyLink text={text} className="ml-auto shrink-0" />
    </div>
  );
}
