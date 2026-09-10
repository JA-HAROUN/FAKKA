import { Link, createFileRoute } from "@tanstack/react-router";
import { Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { RequireAuth } from "@/components/common/RequireAuth";
import { EmptyState } from "@/components/common/EmptyState";
import { PageHeader, Panel, PanelList, Section } from "@/components/common/Section";
import { ActivityFeed } from "@/components/dashboard/ActivityFeed";
import { CreateGroupDialog } from "@/components/dashboard/CreateGroupDialog";
import { GroupRow } from "@/components/dashboard/GroupRow";
import { PeopleBalances } from "@/components/dashboard/PeopleBalances";
import { PositionSummary } from "@/components/dashboard/PositionSummary";
import { useApp } from "@/context/AppContext";
import { useGroupSummaries } from "@/hooks/useGroupSummaries";
import { firstName, pluralize } from "@/utils/format";

export const Route = createFileRoute("/dashboard")({
  head: () => ({
    meta: [
      { title: "Overview — Fakka" },
      {
        name: "description",
        content: "See every group you share expenses with and what you owe or are owed.",
      },
      { property: "og:title", content: "Overview — Fakka" },
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
  const { currentUser } = useApp();
  const { summaries, position, people, activity } = useGroupSummaries();

  // With no groups there is nothing to summarise, so the first run gets one
  // clear next step instead of three empty panels.
  if (summaries.length === 0) {
    return (
      <div className="space-y-6">
        <PageHeader
          title={`Hello, ${firstName(currentUser?.name ?? "there")}`}
          description="Create your first group to start splitting expenses."
        />
        <EmptyState
          icon={Users}
          title="No groups yet"
          description="A group is where you and your friends track what everyone spends. Add the people you split with, then create a group."
          className="py-14"
          action={
            <div className="flex flex-wrap items-center justify-center gap-2">
              <CreateGroupDialog />
              <Button asChild variant="outline">
                <Link to="/friends">Manage friends</Link>
              </Button>
            </div>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-6 lg:space-y-8">
      <PageHeader
        title={`Hello, ${firstName(currentUser?.name ?? "there")}`}
        description={`Your position across ${pluralize(summaries.length, "group")}.`}
        actions={<CreateGroupDialog />}
      />

      <PositionSummary position={position} summaries={summaries} />

      <div className="grid gap-6 lg:gap-8 xl:grid-cols-[minmax(0,1fr)_22rem] 2xl:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="min-w-0 space-y-6 lg:space-y-8">
          <Section title="Your groups" description="Most recently active first.">
            <Panel flush>
              <PanelList>
                {summaries.map((summary) => (
                  <GroupRow key={summary.group.id} summary={summary} />
                ))}
              </PanelList>
            </Panel>
          </Section>

          <ActivityFeed activity={activity} />
        </div>

        <div className="min-w-0">
          <PeopleBalances people={people} />
        </div>
      </div>
    </div>
  );
}
