import { type ClassValue } from "clsx";

import { cn } from "@/common/utils";

export interface CardProps {
  children: React.ReactNode;
  className?: ClassValue;
}

export interface CardHeaderProps {
  children: React.ReactNode;
  className?: ClassValue;
  variant?: "default" | "destructive";
}

export function CardHeader({ children, className, variant = "default" }: CardHeaderProps) {
  return (
    <div
      className={cn(
        "rounded-t-2xl border-b px-4 py-2.5 text-xs font-medium tracking-wider uppercase",
        variant === "default" && "border-border/60 bg-muted/20 text-muted-foreground",
        variant === "destructive" && "border-destructive/50 bg-destructive/10 text-destructive",
        className
      )}
    >
      {children}
    </div>
  );
}

export interface CardContentProps {
  children: React.ReactNode;
  className?: ClassValue;
}

export function CardContent({ children, className }: CardContentProps) {
  return <div className={cn("p-3", className)}>{children}</div>;
}

export interface CardFooterProps {
  children: React.ReactNode;
  className?: ClassValue;
  variant?: "default" | "destructive";
}

export function CardFooter({ children, className, variant = "default" }: CardFooterProps) {
  return (
    <div
      className={cn(
        "mt-auto w-full rounded-b-2xl border-t px-4 py-2 text-sm",
        variant === "default" && "border-border/60 bg-muted/20",
        variant === "destructive" && "border-destructive/50 bg-destructive/10",
        className
      )}
    >
      {children}
    </div>
  );
}

export default function Card({ children, className }: CardProps) {
  return (
    <div className={cn("bg-card/80 border-border flex flex-col rounded-2xl border", className)}>
      {children}
    </div>
  );
}
