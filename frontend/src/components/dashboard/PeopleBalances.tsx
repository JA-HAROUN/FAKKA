import { Link } from "@tanstack/react-router";
import { ChevronRight, HandCoins } from "lucide-react";
import type { ReactNode } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { Panel, PanelList, Section } from "@/components/common/Section";
import { Money } from "@/components/common/Money";
import { UserAvatar } from "@/components/common/UserAvatar";
import { useApp } from "@/context/AppContext";
import type { CounterpartyBalance } from "@/utils/insights";

/**
 * Who owes whom, aggregated across every group the user is in — the question
 * the dashboard exists to answer. Each row states the direction in words
 * before the amount, and links to the group where the debt can be settled.
 */
export function PeopleBalances({ people }: { people: CounterpartyBalance[] }) {
  const { userById, groups } = useApp();

  return (
    <Section
      title="Who owes whom"
      description={
        people.length > 0 ? "Across all your groups, after simplifying debts." : undefined
      }
    >
      {people.length === 0 ? (
        <EmptyState
          icon={HandCoins}
          title="No outstanding balances"
          description="When someone owes you — or you owe them — it shows up here."
        />
      ) : (
        <Panel flush>
          <PanelList>
            {people.map((entry) => {
              const user = userById(entry.userId);
              const theyOweMe = entry.amount > 0;
              const relatedGroups = entry.groupIds
                .map((id) => groups.find((g) => g.id === id))
                .filter((g): g is NonNullable<typeof g> => Boolean(g));
              const soleGroup = relatedGroups.length === 1 ? relatedGroups[0] : undefined;

              const body = (
                <>
                  <UserAvatar user={user} size="md" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-body font-semibold">{user.name}</p>
                    <p className="truncate text-caption text-muted-foreground">
                      {theyOweMe ? "owes you" : "you owe"}
                      {relatedGroups.length > 0 &&
                        ` · ${relatedGroups.map((g) => g.name).join(", ")}`}
                    </p>
                  </div>
                  <Money
                    value={Math.abs(entry.amount)}
                    size="lg"
                    tone={theyOweMe ? "positive" : "negative"}
                  />
                </>
              );

              return (
                <Row key={entry.userId} groupId={soleGroup?.id}>
                  {body}
                </Row>
              );
            })}
          </PanelList>
        </Panel>
      )}
    </Section>
  );
}

/** Links straight to the group when the balance comes from just one. */
function Row({ groupId, children }: { groupId?: string | undefined; children: ReactNode }) {
  if (!groupId) {
    return (
      <li className="flex items-center gap-3 p-4">
        {children}
        <span className="size-4 shrink-0" aria-hidden />
      </li>
    );
  }
  return (
    <li>
      <Link
        to="/group/$groupId"
        params={{ groupId }}
        className="row-hover flex items-center gap-3 p-4"
        title="Open group to settle up"
      >
        {children}
        <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      </Link>
    </li>
  );
}
