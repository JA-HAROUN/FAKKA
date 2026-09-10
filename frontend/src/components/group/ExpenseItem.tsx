import { Sparkles, ScanLine } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { Expense } from "@/types";
import { useApp } from "@/context/AppContext";
import { formatAmount, round2 } from "@/utils/calculations";
import { getCategory } from "@/utils/categories";

export function ExpenseItem({ expense }: { expense: Expense }) {
  const { userById, currentUser } = useApp();
  const category = getCategory(expense.category);
  const Icon = category.icon;
  const payer = userById(expense.paidBy);
  const myShare = currentUser ? round2(expense.shares[currentUser.id] ?? 0) : 0;

  return (
    <li className="flex items-start gap-3 p-4">
      <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-secondary text-secondary-foreground">
        <Icon className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate font-medium">{expense.description}</p>
          {expense.source === "ai" && (
            <Badge variant="secondary" className="gap-1 text-[10px]">
              <Sparkles className="size-3" /> AI
            </Badge>
          )}
          {expense.source === "receipt" && (
            <Badge variant="secondary" className="gap-1 text-[10px]">
              <ScanLine className="size-3" /> Scan
            </Badge>
          )}
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground">
          {payer.name} paid · {category.label} ·{" "}
          {new Date(expense.createdAt).toLocaleDateString(undefined, {
            day: "numeric",
            month: "short",
            year: "numeric",
          })}
        </p>
      </div>
      <div className="text-right">
        <p className="font-semibold tabular-nums">{formatAmount(expense.totalAmount)}</p>
        <p className="text-xs text-muted-foreground">your share {formatAmount(myShare)}</p>
      </div>
    </li>
  );
}
