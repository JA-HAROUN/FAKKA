import { ArrowRight, CheckCircle2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { Group } from "@/types";
import { useApp } from "@/context/AppContext";
import { formatAmount, groupBalances, simplifyDebts } from "@/utils/calculations";

export function SettlementList({ group }: { group: Group }) {
  const { userById, expensesOfGroup, settlementsOfGroup, markSettlementPaid } = useApp();
  const settlements = settlementsOfGroup(group.id);
  const balances = groupBalances(group.members, expensesOfGroup(group.id), settlements);
  const pending = simplifyDebts(balances);
  const paid = settlements.filter((s) => s.status === "paid");

  return (
    <section className="card-soft p-5">
      <h2 className="text-base font-semibold">Who owes whom</h2>
      <p className="text-sm text-muted-foreground">Simplified to the fewest possible transfers.</p>

      <ul className="mt-4 space-y-2">
        {pending.length === 0 && paid.length === 0 && (
          <li className="rounded-xl bg-neutral-soft px-4 py-6 text-center text-sm text-muted-foreground">
            Nothing to settle yet.
          </li>
        )}

        {pending.length === 0 && paid.length > 0 && (
          <li className="rounded-xl bg-positive-soft px-4 py-4 text-center text-sm font-medium text-positive">
            🎉 Everyone is settled up.
          </li>
        )}

        {pending.map((d) => (
          <li
            key={d.id}
            className="flex flex-wrap items-center gap-3 rounded-xl border border-border p-3"
          >
            <div className="flex min-w-0 flex-1 items-center gap-2 text-sm">
              <span className="truncate font-medium">{userById(d.fromUser).name}</span>
              <ArrowRight className="size-4 shrink-0 text-muted-foreground" />
              <span className="truncate font-medium">{userById(d.toUser).name}</span>
            </div>
            <span className="font-semibold tabular-nums text-negative">{formatAmount(d.amount)}</span>
            <Badge variant="outline" className="border-accent-foreground/30 text-accent-foreground">
              Pending
            </Badge>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                markSettlementPaid({
                  groupId: group.id,
                  fromUser: d.fromUser,
                  toUser: d.toUser,
                  amount: d.amount,
                });
                toast.success(
                  `Marked ${userById(d.fromUser).name} → ${userById(d.toUser).name} as paid`,
                );
              }}
            >
              Mark as paid
            </Button>
          </li>
        ))}

        {paid.map((s) => (
          <li
            key={s.id}
            className="flex flex-wrap items-center gap-3 rounded-xl border border-border bg-positive-soft/50 p-3"
          >
            <div className="flex min-w-0 flex-1 items-center gap-2 text-sm">
              <span className="truncate font-medium">{userById(s.fromUser).name}</span>
              <ArrowRight className="size-4 shrink-0 text-muted-foreground" />
              <span className="truncate font-medium">{userById(s.toUser).name}</span>
            </div>
            <span className="font-semibold tabular-nums text-positive">{formatAmount(s.amount)}</span>
            <Badge className="gap-1 bg-positive text-positive-foreground">
              <CheckCircle2 className="size-3" /> Paid
            </Badge>
          </li>
        ))}
      </ul>
    </section>
  );
}
