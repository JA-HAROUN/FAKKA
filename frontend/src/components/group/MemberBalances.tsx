import { BalanceIndicator } from "@/components/common/Money";
import { Panel, PanelList, Section } from "@/components/common/Section";
import { UserAvatar } from "@/components/common/UserAvatar";
import { useApp } from "@/context/AppContext";
import { round2 } from "@/utils/calculations";

/** Per-member position in the group: paid minus owed, stated per person. */
export function MemberBalances({
  memberIds,
  balances,
}: {
  memberIds: string[];
  balances: Record<string, number>;
}) {
  const { userById, currentUser } = useApp();

  const sorted = [...memberIds].sort(
    (a, b) => Math.abs(balances[b] ?? 0) - Math.abs(balances[a] ?? 0),
  );

  return (
    <Section title="Member balances" description="Paid minus owed, per person.">
      <Panel flush>
        <PanelList>
          {sorted.map((id) => {
            const user = userById(id);
            const balance = round2(balances[id] ?? 0);
            const isMe = currentUser?.id === id;
            return (
              <li key={id} className="flex items-center gap-3 px-4 py-3">
                <UserAvatar user={user} size="sm" />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">
                  {user.name}
                  {isMe && <span className="ml-1.5 text-xs text-muted-foreground">you</span>}
                </span>
                <BalanceIndicator
                  amount={balance}
                  label={balance > 0.005 ? "is owed" : balance < -0.005 ? "owes" : "settled"}
                />
              </li>
            );
          })}
        </PanelList>
      </Panel>
    </Section>
  );
}
