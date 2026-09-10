import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Money } from "@/components/common/Money";
import { formatAmount, toneOf } from "@/utils/calculations";
import { pluralize } from "@/utils/format";
import type { GroupSummary, OverallPosition } from "@/utils/insights";

/**
 * The first thing on the dashboard: the user's overall position stated in
 * words, then the two figures behind it. No decorative widgets — every number
 * here answers "what do I owe / what am I owed".
 */
export function PositionSummary({
  position,
  summaries,
}: {
  position: OverallPosition;
  summaries: GroupSummary[];
}) {
  const tone = toneOf(position.net);
  const owedGroups = summaries.filter((s) => s.myBalance > 0.005).length;
  const owingGroups = summaries.filter((s) => s.myBalance < -0.005).length;

  const summaryLine =
    tone === "positive"
      ? `You are owed ${formatAmount(position.net)} more than you owe.`
      : tone === "negative"
        ? `You owe ${formatAmount(position.net)} more than you are owed.`
        : summaries.length === 0
          ? "Create a group to start tracking shared expenses."
          : "Everything is settled across your groups.";

  return (
    <section className="panel p-5 sm:p-6" aria-label="Your overall position">
      <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-10">
        <div className="space-y-1.5">
          <h2 className="text-xs font-medium text-muted-foreground">Net balance</h2>
          <p>
            <Money value={position.net} size="2xl" tone={tone} signed />
          </p>
          <p className="text-sm text-muted-foreground">{summaryLine}</p>
        </div>

        <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-border bg-border md:min-w-[22rem]">
          <Figure
            icon={ArrowDownLeft}
            tone="positive"
            label="You are owed"
            value={position.owed}
            hint={
              owedGroups > 0 ? `across ${pluralize(owedGroups, "group")}` : "nothing outstanding"
            }
          />
          <Figure
            icon={ArrowUpRight}
            tone="negative"
            label="You owe"
            value={position.owing}
            hint={
              owingGroups > 0 ? `across ${pluralize(owingGroups, "group")}` : "nothing outstanding"
            }
          />
        </dl>
      </div>
    </section>
  );
}

function Figure({
  icon: Icon,
  tone,
  label,
  value,
  hint,
}: {
  icon: typeof ArrowDownLeft;
  tone: "positive" | "negative";
  label: string;
  value: number;
  hint: string;
}) {
  const isZero = value < 0.005;
  return (
    <div className="bg-card p-4">
      <dt className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
        <Icon
          className={tone === "positive" ? "size-3.5 text-positive" : "size-3.5 text-negative"}
          aria-hidden
        />
        {label}
      </dt>
      <dd className="mt-1.5">
        <Money value={value} size="lg" tone={isZero ? "neutral" : tone} />
        <span className="mt-0.5 block text-xs text-muted-foreground">{hint}</span>
      </dd>
    </div>
  );
}
