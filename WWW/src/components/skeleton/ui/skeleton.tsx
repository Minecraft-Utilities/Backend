import { cn } from "@/common/utils";

export interface SkeletonProps {
  className?: string;
  children?: React.ReactNode;
}

export default function Skeleton({ className, children }: SkeletonProps) {
  return <div className={cn("bg-muted/30 animate-pulse rounded-md", className)}>{children}</div>;
}
