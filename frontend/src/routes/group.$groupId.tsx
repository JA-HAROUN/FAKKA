import { createFileRoute, Link } from "@tanstack/react-router";
import { SearchX } from "lucide-react";
import { RequireAuth } from "@/components/common/RequireAuth";
import { EmptyState } from "@/components/common/EmptyState";
import { GroupHeader } from "@/components/group/GroupHeader";
import { ExpenseList } from "@/components/group/ExpenseList";
import { MemberBalances } from "@/components/group/MemberBalances";
import { SettleUpPanel } from "@/components/group/SettleUpPanel";
import { ExportButton } from "@/components/group/ExportButton";
import { AddExpenseModal } from "@/components/expense-modal/AddExpenseModal";
import { Button } from "@/components/ui/button";
import { useApp } from "@/context/AppContext";
import { groupBalances, groupTotal, round2, simplifyDebts } from "@/utils/calculations";

export const Route = createFileRoute("/group/$groupId")({
  head: () => ({
    meta: [
      { title: "Group expenses — Fakka" },
      {
        name: "description",
        content: "Every expense in this group, simplified balances, and who should pay whom.",
      },
      { property: "og:title", content: "Group expenses — Fakka" },
      {
        property: "og:description",
        content: "Track group spending and settle up in the fewest possible payments.",
      },
    ],
  }),
  component: () => (
    <RequireAuth>
      <GroupPage />
    </RequireAuth>
  ),
});

function GroupPage() {
  const { groupId } = Route.useParams();
  const { groups, currentUser, expensesOfGroup, settlementsOfGroup } = useApp();
  const group = groups.find((g) => g.id === groupId);

  if (!group) {
    return (
      <EmptyState
        icon={SearchX}
        title="Group not found"
        description="This group may have been removed, or the link is out of date."
        action={
          <Button asChild variant="outline">
            <Link to="/dashboard">Back to all groups</Link>
          </Button>
        }
      />
    );
  }

  const expenses = expensesOfGroup(group.id);
  const settlements = settlementsOfGroup(group.id);
  const balances = groupBalances(group.members, expenses, settlements);
  const pending = simplifyDebts(balances);
  const myBalance = currentUser ? round2(balances[currentUser.id] ?? 0) : 0;

  return (
    <div className="space-y-6 lg:space-y-8">
      <GroupHeader
        group={group}
        total={groupTotal(expenses)}
        myBalance={myBalance}
        actions={
          <>
            <AddExpenseModal group={group} />
            <ExportButton group={group} />
          </>
        }
      />

      {/* Settle-up leads on mobile (it is the action), while desktop keeps the
          expense ledger in the primary column. */}
      <div className="grid gap-6 lg:gap-8 xl:grid-cols-[minmax(0,1fr)_22rem] xl:content-start 2xl:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="min-w-0 xl:col-start-2 xl:row-span-2 xl:row-start-1">
          <SettleUpPanel group={group} pending={pending} settled={settlements} />
        </div>

        <div className="min-w-0 xl:col-start-1 xl:row-start-1">
          <ExpenseList expenses={expenses} emptyAction={<AddExpenseModal group={group} />} />
        </div>

        <div className="min-w-0 xl:col-start-1 xl:row-start-2">
          <MemberBalances memberIds={group.members} balances={balances} />
        </div>
      </div>
    </div>
  );
}
