import type { ReactNode } from "react";
import { ReceiptText } from "lucide-react";
import type { Expense } from "@/types";
import { EmptyState } from "@/components/common/EmptyState";
import { Panel, PanelList, Section } from "@/components/common/Section";
import { formatAmount, groupTotal } from "@/utils/calculations";
import { dayKey, formatDayHeading, pluralize } from "@/utils/format";
import { ExpenseItem } from "./ExpenseItem";

/**
 * Expenses grouped by day, newest first. The day headings give the list a
 * rhythm to scan and remove the repeated date from every row.
 */
export function ExpenseList({
  expenses,
  emptyAction,
}: {
  expenses: Expense[];
  emptyAction?: ReactNode;
}) {
  const sorted = [...expenses].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  const days: { key: string; heading: string; items: Expense[] }[] = [];
  for (const expense of sorted) {
    const key = dayKey(expense.createdAt);
    const current = days.at(-1);
    if (current?.key === key) current.items.push(expense);
    else days.push({ key, heading: formatDayHeading(expense.createdAt), items: [expense] });
  }

  return (
    <Section
      title="Expenses"
      description={
        sorted.length > 0
          ? `${pluralize(sorted.length, "expense")} · ${formatAmount(groupTotal(sorted))} total`
          : undefined
      }
    >
      {sorted.length === 0 ? (
        <EmptyState
          icon={ReceiptText}
          title="No expenses yet"
          description="Add the first shared expense and Fakka works out who owes whom."
          action={emptyAction}
        />
      ) : (
        <Panel flush>
          {days.map((day) => (
            <div key={day.key} className="border-t border-border first:border-t-0">
              <h3 className="eyebrow border-b border-border bg-surface px-4 py-2.5 sm:px-5">
                {day.heading}
              </h3>
              <PanelList>
                {day.items.map((expense) => (
                  <ExpenseItem key={expense.id} expense={expense} />
                ))}
              </PanelList>
            </div>
          ))}
        </Panel>
      )}
    </Section>
  );
}
