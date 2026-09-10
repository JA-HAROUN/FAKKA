import { cn } from "@/lib/utils";
import { balanceLabel, formatSigned, toneOf } from "@/utils/calculations";

const toneClasses = {
  positive: "bg-positive-soft text-positive",
  negative: "bg-negative-soft text-negative",
  neutral: "bg-neutral-soft text-muted-foreground",
} as const;

const dotClasses = {
  positive: "bg-positive",
  negative: "bg-negative",
  neutral: "bg-neutral",
} as const;

export function BalancePill({
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
        "inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-sm font-semibold",
        toneClasses[tone],
        className,
      )}
    >
      <span className={cn("size-2 rounded-full", dotClasses[tone])} aria-hidden />
      {formatSigned(amount)}
      <span className="font-medium opacity-80">— {label ?? balanceLabel(amount)}</span>
    </span>
  );
}

export function BalanceAmount({ amount, className }: { amount: number; className?: string }) {
  const tone = toneOf(amount);
  return (
    <span
      className={cn(
        "font-semibold tabular-nums",
        tone === "positive" && "text-positive",
        tone === "negative" && "text-negative",
        tone === "neutral" && "text-muted-foreground",
        className,
      )}
    >
      {formatSigned(amount)}
    </span>
  );
}
