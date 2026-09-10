import { ScanLine, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Money } from "@/components/common/Money";
import type { Expense } from "@/types";
import { useApp } from "@/context/AppContext";
import { round2 } from "@/utils/calculations";
import { formatNumber } from "@/utils/format";
import { getCategory } from "@/utils/categories";

/**
 * One expense, ordered by what a reader needs first: what it was, who paid,
 * then the amount and the share that actually affects the reader.
 */
export function ExpenseItem({ expense }: { expense: Expense }) {
  const { userById, currentUser } = useApp();
  const category = getCategory(expense.category);
  const Icon = category.icon;
  const payer = userById(expense.paidBy);
  const iPaid = currentUser?.id === expense.paidBy;
  const myShare = currentUser ? round2(expense.shares[currentUser.id] ?? 0) : 0;
  const included = myShare > 0.005;

  return (
    <li className="flex items-start gap-3 px-4 py-3">
      <span
        className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg border border-border bg-surface text-muted-foreground"
        aria-hidden
      >
        <Icon className="size-4" />
      </span>

      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
          <p className="text-sm font-medium">{expense.description}</p>
          {expense.source === "ai" && (
            <Badge variant="outline" title="Created from a plain-language description">
              <Sparkles aria-hidden /> AI
            </Badge>
          )}
          {expense.source === "receipt" && (
            <Badge variant="outline" title="Created from a scanned receipt">
              <ScanLine aria-hidden /> Receipt
            </Badge>
          )}
        </div>
        <p className="mt-0.5 truncate text-xs text-muted-foreground">
          {iPaid ? "You paid" : `${payer.name} paid`} · {category.label}
        </p>
      </div>

      <div className="shrink-0 text-right">
        <Money value={expense.totalAmount} size="sm" />
        <p className="text-xs text-muted-foreground">
          {included ? (
            <>
              your share <span className="tabular-nums">{formatNumber(myShare)}</span>
            </>
          ) : (
            "not your split"
          )}
        </p>
      </div>
    </li>
  );
}
