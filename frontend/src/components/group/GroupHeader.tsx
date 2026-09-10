import { Link } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import { AvatarStack } from "@/components/common/UserAvatar";
import { BalancePill } from "@/components/common/BalancePill";
import type { Group } from "@/types";
import { useApp } from "@/context/AppContext";
import { formatAmount, groupBalances, groupTotal } from "@/utils/calculations";

export function GroupHeader({ group }: { group: Group }) {
  const { userById, currentUser, expensesOfGroup, settlementsOfGroup } = useApp();
  const expenses = expensesOfGroup(group.id);
  const balances = groupBalances(group.members, expenses, settlementsOfGroup(group.id));
  const myBalance = currentUser ? (balances[currentUser.id] ?? 0) : 0;

  return (
    <div className="space-y-4">
      <Link
        to="/dashboard"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> All groups
      </Link>

      <div className="card-soft flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
        <span className="grid size-16 shrink-0 place-items-center rounded-2xl bg-primary-soft text-3xl">
          {group.image}
        </span>
        <div className="min-w-0 flex-1 space-y-2">
          <h1 className="truncate text-2xl font-bold tracking-tight">{group.name}</h1>
          <div className="flex flex-wrap items-center gap-3">
            <AvatarStack users={group.members.map(userById)} max={5} />
            <span className="text-sm text-muted-foreground">
              Total spent{" "}
              <strong className="text-foreground tabular-nums">
                {formatAmount(groupTotal(expenses))}
              </strong>
            </span>
          </div>
        </div>
        <BalancePill amount={myBalance} className="self-start sm:self-center" />
      </div>
    </div>
  );
}

export function MemberBalances({ group }: { group: Group }) {
  const { userById, currentUser, expensesOfGroup, settlementsOfGroup } = useApp();
  const balances = groupBalances(
    group.members,
    expensesOfGroup(group.id),
    settlementsOfGroup(group.id),
  );

  return (
    <section className="card-soft p-5">
      <h2 className="text-base font-semibold">Member balances</h2>
      <ul className="mt-3 divide-y divide-border">
        {group.members.map((id) => {
          const user = userById(id);
          const b = balances[id] ?? 0;
          return (
            <li key={id} className="flex items-center gap-3 py-3">
              <span className="grid size-9 place-items-center rounded-full bg-secondary text-base">
                {user.avatar}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm font-medium">
                {user.name}
                {currentUser?.id === id && (
                  <span className="ml-1 text-xs text-muted-foreground">(you)</span>
                )}
              </span>
              <BalancePillCompact amount={b} />
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function BalancePillCompact({ amount }: { amount: number }) {
  return <BalancePill amount={amount} label={amount > 0 ? "is owed" : amount < 0 ? "owes" : "settled"} />;
}
