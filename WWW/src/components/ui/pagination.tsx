import { cn, formatNumberWithCommas } from "@/common/utils";
import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import React from "react";
import { Button } from "./button";

type PageButtonProps = {
  page: number;
  currentPage: number;
  isActive?: boolean;
  children: React.ReactNode;
  href: string;
};

function PageButton({ page: buttonPage, currentPage, isActive, children, href }: PageButtonProps) {
  const isCurrentPage = buttonPage === currentPage;

  const className = cn(
    "relative h-9 min-w-10 px-3 transition-all duration-200",
    "whitespace-nowrap",
    isCurrentPage && "cursor-default",
    !isActive && "hover:shadow-sm"
  );

  if (isCurrentPage) {
    return (
      <Button
        variant={isActive ? "default" : "ghost"}
        size="sm"
        className={className}
        aria-current="page"
        aria-label={`Current page, page ${buttonPage}`}
      >
        {children}
      </Button>
    );
  }

  return (
    <Button asChild variant={isActive ? "default" : "ghost"} size="sm" className={className}>
      <Link href={href} aria-label={`Go to page ${buttonPage}`} className="flex items-center justify-center">
        {children}
      </Link>
    </Button>
  );
}

type NavigationButtonProps = {
  href: string;
  disabled: boolean;
  children: React.ReactNode;
  ariaLabel: string;
};

function NavigationButton({ href, disabled, children, ariaLabel }: NavigationButtonProps) {
  const className = cn(
    "transition-all duration-200",
    disabled && "cursor-not-allowed opacity-50 pointer-events-none",
    !disabled && "hover:shadow-sm"
  );

  if (disabled) {
    return (
      <Button variant="ghost" size="icon" disabled className={className} aria-label={ariaLabel}>
        {children}
      </Button>
    );
  }

  return (
    <Button asChild variant="ghost" size="icon" className={className} aria-label={ariaLabel}>
      <Link href={href}>{children}</Link>
    </Button>
  );
}

export type SimpleProps = {
  page: number;
  totalItems: number;
  itemsPerPage: number;
  basePath?: string;
  hrefForPage?: (page: number) => string;
};

export default function Pagination({ page, totalItems, itemsPerPage, basePath, hrefForPage }: SimpleProps) {
  page = page === 0 ? 1 : page;
  const totalPages = itemsPerPage > 0 ? Math.ceil(totalItems / itemsPerPage) : 0;

  const href =
    hrefForPage ??
    ((targetPage: number) => (basePath != null ? `${basePath.replace(/\/$/, "")}/${targetPage}` : "#"));

  const pageNumbers: React.ReactNode[] = [];

  let startPage = Math.max(1, page - Math.floor(3 / 2));
  const endPage = Math.min(totalPages, startPage + 3 - 1);

  if (totalPages > 3 && endPage - startPage + 1 < 3) {
    startPage = endPage - 3 + 1;
  }

  for (let pageNum = startPage; pageNum <= endPage; pageNum++) {
    pageNumbers.push(
      <PageButton
        key={`page-${pageNum}`}
        page={pageNum}
        isActive={pageNum === page}
        currentPage={page}
        href={href(pageNum)}
      >
        {formatNumberWithCommas(pageNum)}
      </PageButton>
    );
  }

  return (
    <div className={cn("relative flex w-full items-center justify-center")}>
      <nav className="flex flex-wrap items-center justify-center gap-1.5" aria-label="Pagination navigation">
        <NavigationButton href={href(page - 1)} disabled={page <= 1} ariaLabel="Go to previous page">
          <ChevronLeft className="h-4 w-4" />
        </NavigationButton>
        {pageNumbers}
        <NavigationButton href={href(page + 1)} disabled={page >= totalPages} ariaLabel="Go to next page">
          <ChevronRight className="h-4 w-4" />
        </NavigationButton>
      </nav>
    </div>
  );
}
