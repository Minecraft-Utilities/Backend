"use client";

import { cn } from "@/common/utils";
import Link from "next/link";
import { useState } from "react";

export interface SimpleLinkProps extends React.ComponentProps<typeof Link> {
  children: React.ReactNode;
  href: string;
  className?: string;
}

export default function SimpleLink({ children, href, className, ...props }: SimpleLinkProps) {
  const isExternal = href.startsWith("http");

  const [isHovering, setIsHovering] = useState(false);

  return (
    <Link
      {...props}
      href={href}
      prefetch={isHovering}
      onMouseEnter={() => setIsHovering(true)}
      onMouseLeave={() => setIsHovering(false)}
      target={isExternal ? "_blank" : undefined}
      rel={isExternal ? "noopener noreferrer" : undefined}
      draggable={false}
      className={cn("hover:text-primary/80 transition-colors duration-200", className)}
      aria-label={isExternal ? `Open ${href} in a new tab` : undefined}
    >
      {children}
    </Link>
  );
}
