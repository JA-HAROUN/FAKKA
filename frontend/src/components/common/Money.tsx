import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { CURRENCY, balanceLabel, formatAmount, formatSigned, toneOf } from "@/utils/calculations";

/**
 * Every currency figure in Fakka renders through here, so formatting, tabular
 * alignment and the green/red/grey meaning stay identical app-wide.
 *
 * Colour never carries the meaning on its own: `BalanceStatement` and
 * `BalanceIndicator` always pair the tone with a written label.
 */

export type Tone = "positive" | "negative" | "neutral" | "plain";

const toneText: Record<Tone, string> = {
  positive: "text-positive",
  negative: "text-negative",
  neutral: "text-muted-foreground",
  plain: "text-foreground",
};

const toneSurface: Record<Tone, string> = {
  positive: "bg-positive-soft text-positive",
  negative: "bg-negative-soft text-negative",
  neutral: "bg-neutral-soft text-muted-foreground",
  plain: "bg-surface text-foreground",
};

const toneDot: Record<Tone, string> = {
  positive: "bg-positive",
  negative: "bg-negative",
  neutral: "bg-neutral",
  plain: "bg-foreground",
};

const sizes = {
  xs: "text-xs",
  sm: "text-[13px]",
  md: "text-sm",
  lg: "text-lg",
  xl: "text-2xl tracking-tight",
  "2xl": "text-2xl tracking-tight sm:text-3xl",
} as const;

export function Money({
  value,
  size = "md",
  tone = "plain",
  signed = false,
  className,
}: {
  value: number;
  size?: keyof typeof sizes;
  /** `"auto"` derives the tone from the sign of `value`. */
  tone?: Tone | "auto";
  signed?: boolean;
  className?: string;
}) {
  const resolved: Tone = tone === "auto" ? toneOf(value) : tone;
  return (
    <span className={cn("font-semibold tabular-nums", sizes[size], toneText[resolved], className)}>
      {signed ? formatSigned(value) : formatAmount(value)}
    </span>
  );
}

/**
 * The primary financial reading pattern: a label, then the figure, then who
 * it involves — "You owe / EGP 350 / to Ahmed Hassan" reads faster than a
 * number next to a name.
 */
export function BalanceStatement({
  amount,
  party,
  size = "xl",
  label,
  className,
}: {
  amount: number;
  party?: ReactNode;
  size?: keyof typeof sizes;
  label?: string;
  className?: string;
}) {
  const tone = toneOf(amount);
  return (
    <div className={cn("space-y-0.5", className)}>
      <p className="text-xs font-medium text-muted-foreground">{label ?? balanceLabel(amount)}</p>
      <p>
        <Money value={amount} size={size} tone={tone} />
        {tone === "neutral" && !party && (
          <span className="ml-1.5 text-xs font-medium text-muted-foreground">all settled</span>
        )}
      </p>
      {party && (
        <p className="truncate text-xs text-muted-foreground">
          {tone === "negative" ? "to" : "from"} {party}
        </p>
      )}
    </div>
  );
}

/**
 * Compact balance chip for list rows and cards. Dot + label + amount, so the
 * state survives without colour perception.
 */
export function BalanceIndicator({
  amount,
  label,
  className,
}: {
  amount: number;
  label?: string;
  className?: string;
}) {
  const tone = toneOf(amount);
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium",
        toneSurface[tone],
        className,
      )}
    >
      <span className={cn("size-1.5 shrink-0 rounded-full", toneDot[tone])} aria-hidden />
      <span>{label ?? balanceLabel(amount)}</span>
      {tone !== "neutral" && (
        <span className="font-semibold tabular-nums">{formatAmount(amount)}</span>
      )}
    </span>
  );
}
