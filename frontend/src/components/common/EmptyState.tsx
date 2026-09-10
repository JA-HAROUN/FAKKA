import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Empty and zero states. A zero balance is a legitimate state, not a failure,
 * so the treatment stays quiet: one icon, one line of what's missing, one line
 * of what to do about it.
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
  bordered = true,
}: {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
  bordered?: boolean;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-2 px-6 py-10 text-center",
        bordered && "rounded-2xl border border-dashed border-border bg-card/50",
        className,
      )}
    >
      <span className="grid size-10 place-items-center rounded-lg bg-surface text-muted-foreground">
        <Icon className="size-5" aria-hidden />
      </span>
      <h3 className="text-sm font-semibold">{title}</h3>
      {description && (
        <p className="max-w-xs text-sm text-balance text-muted-foreground">{description}</p>
      )}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
