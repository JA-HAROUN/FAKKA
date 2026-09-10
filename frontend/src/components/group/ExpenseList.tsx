import type { Expense } from "@/types";
import { EmptyState } from "@/components/common/EmptyState";
import { ExpenseItem } from "./ExpenseItem";

export function ExpenseList({ expenses }: { expenses: Expense[] }) {
  const sorted = [...expenses].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <section className="space-y-3">
      <h2 className="text-base font-semibold">Expenses</h2>
      {sorted.length === 0 ? (
        <EmptyState
          emoji="🧾"
          title="No expenses yet"
          description="Add the first shared expense and Fakka will work out who owes whom."
        />
      ) : (
        <ul className="card-soft divide-y divide-border">
          {sorted.map((e) => (
            <ExpenseItem key={e.id} expense={e} />
          ))}
        </ul>
      )}
    </section>
  );
}
