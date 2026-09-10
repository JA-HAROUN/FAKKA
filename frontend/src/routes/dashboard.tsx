import { createFileRoute } from "@tanstack/react-router";
import { RequireAuth } from "@/components/common/RequireAuth";
import { BalanceSummary } from "@/components/dashboard/BalanceSummary";
import { CreateGroupDialog } from "@/components/dashboard/CreateGroupDialog";
import { GroupCard } from "@/components/dashboard/GroupCard";
import { EmptyState } from "@/components/common/EmptyState";
import { useApp } from "@/context/AppContext";

export const Route = createFileRoute("/dashboard")({
  head: () => ({
    meta: [
      { title: "Your groups — Fakka" },
      {
        name: "description",
        content: "See every group you share expenses with and what you owe or are owed.",
      },
      { property: "og:title", content: "Your groups — Fakka" },
      {
        property: "og:description",
        content: "All your shared expense groups and balances in one place.",
      },
    ],
  }),
  component: () => (
    <RequireAuth>
      <DashboardPage />
    </RequireAuth>
  ),
});

function DashboardPage() {
  const { groups, currentUser } = useApp();
  const myGroups = groups.filter((g) => currentUser && g.members.includes(currentUser.id));

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            Hey {currentUser?.name.split(" ")[0]} 👋
          </h1>
          <p className="text-sm text-muted-foreground">Here's where your money stands.</p>
        </div>
        <CreateGroupDialog />
      </div>

      <BalanceSummary />

      <section className="space-y-3">
        <h2 className="text-base font-semibold">Your groups</h2>
        {myGroups.length === 0 ? (
          <EmptyState
            emoji="👥"
            title="No groups yet"
            description="Create a group to start splitting expenses with your friends."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {myGroups.map((g) => (
              <GroupCard key={g.id} group={g} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
