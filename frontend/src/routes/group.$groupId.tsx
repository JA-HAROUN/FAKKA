import { createFileRoute, Link } from "@tanstack/react-router";
import { RequireAuth } from "@/components/common/RequireAuth";
import { GroupHeader } from "@/components/group/GroupHeader";
import { ExpenseList } from "@/components/group/ExpenseList";
import { SettlementList } from "@/components/group/SettlementList";
import { ExportButton } from "@/components/group/ExportButton";
import { AddExpenseModal } from "@/components/expense-modal/AddExpenseModal";
import { EmptyState } from "@/components/common/EmptyState";
import { useApp } from "@/context/AppContext";

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
  const { groups, expensesOfGroup } = useApp();
  const group = groups.find((g) => g.id === groupId);

  if (!group) {
    return (
      <div className="space-y-4">
        <EmptyState
          emoji="🔍"
          title="Group not found"
          description="This group may have been removed."
        />
        <Link to="/dashboard" className="text-sm font-medium text-primary hover:underline">
          Back to all groups
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <GroupHeader group={group} />

      <div className="flex flex-wrap items-center gap-2">
        <AddExpenseModal group={group} />
        <ExportButton group={group} />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <ExpenseList expenses={expensesOfGroup(group.id)} />
        <SettlementList group={group} />
      </div>
    </div>
  );
}
