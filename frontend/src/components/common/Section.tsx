import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * The page-level building blocks. A screen is a stack of `Section`s; a
 * section's body is a list, a table or a `Panel` — not another nest of cards.
 */

export function PageHeader({
  title,
  description,
  actions,
  back,
  className,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  /** Rendered above the title — e.g. a back link on detail pages. */
  back?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("space-y-3", className)}>
      {back}
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
        <div className="min-w-0 space-y-1">
          <h1 className="truncate text-xl font-semibold tracking-tight sm:text-2xl">{title}</h1>
          {description && <p className="text-sm text-muted-foreground">{description}</p>}
        </div>
        {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}

export function Section({
  title,
  description,
  actions,
  children,
  className,
  headingLevel = "h2",
}: {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  headingLevel?: "h2" | "h3";
}) {
  const Heading = headingLevel;
  return (
    <section className={cn("space-y-3", className)}>
      {(title || actions) && (
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
          <div className="min-w-0">
            {title && <Heading className="section-label truncate">{title}</Heading>}
            {description && <p className="mt-0.5 text-xs text-muted-foreground">{description}</p>}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

/** A bordered surface. Use `flush` when the content is an edge-to-edge list. */
export function Panel({
  children,
  className,
  flush = false,
}: {
  children: ReactNode;
  className?: string;
  flush?: boolean;
}) {
  return (
    <div className={cn("panel", flush ? "overflow-hidden" : "p-4 sm:p-5", className)}>
      {children}
    </div>
  );
}

/** Edge-to-edge list inside a `Panel flush` — hairline dividers, no shadows. */
export function PanelList({ children, className }: { children: ReactNode; className?: string }) {
  return <ul className={cn("divide-y divide-border", className)}>{children}</ul>;
}
