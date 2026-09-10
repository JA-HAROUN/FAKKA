import { useApp } from "@/context/AppContext";
import { formatAmount, groupBalances, round2 } from "@/utils/calculations";

export function BalanceSummary() {
  const { groups, currentUser, expensesOfGroup, settlementsOfGroup } = useApp();

  let owed = 0;
  let owing = 0;
  for (const g of groups) {
    if (!currentUser || !g.members.includes(currentUser.id)) continue;
    const b = round2(
      groupBalances(g.members, expensesOfGroup(g.id), settlementsOfGroup(g.id))[currentUser.id] ?? 0,
    );
    if (b > 0) owed += b;
    else owing += -b;
  }
  const net = round2(owed - owing);

  const cards = [
    { label: "You are owed", value: owed, tone: "text-positive", bg: "bg-positive-soft" },
    { label: "You owe", value: owing, tone: "text-negative", bg: "bg-negative-soft" },
    {
      label: "Net balance",
      value: net,
      tone: net > 0 ? "text-positive" : net < 0 ? "text-negative" : "text-muted-foreground",
      bg: "bg-secondary",
    },
  ];

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
      {cards.map((c) => (
        <div key={c.label} className={`rounded-2xl p-4 ${c.bg}`}>
          <p className="text-xs font-medium text-muted-foreground">{c.label}</p>
          <p className={`mt-1 text-xl font-bold tabular-nums ${c.tone}`}>{formatAmount(c.value)}</p>
        </div>
      ))}
    </div>
  );
}
