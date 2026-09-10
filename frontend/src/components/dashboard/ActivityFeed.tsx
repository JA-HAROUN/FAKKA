import { Link } from "@tanstack/react-router";
import { CheckCircle2, Clock } from "lucide-react";
import { EmptyState } from "@/components/common/EmptyState";
import { Panel, PanelList, Section } from "@/components/common/Section";
import { Money } from "@/components/common/Money";
import { useApp } from "@/context/AppContext";
import { getCategory } from "@/utils/categories";
import { formatRelativeDate } from "@/utils/format";
import type { ActivityEvent } from "@/utils/insights";

/** Newest expenses and completed settlements — what changed since last visit. */
export function ActivityFeed({ activity }: { activity: ActivityEvent[] }) {
  const { groups, userById, currentUser } = useApp();
  const groupName = (id: string) => groups.find((g) => g.id === id)?.name ?? "Group";

  return (
    <Section title="Recent activity">
      {activity.length === 0 ? (
        <EmptyState
          icon={Clock}
          title="Nothing yet"
          description="Expenses and settlements will appear here as they happen."
        />
      ) : (
        <Panel flush>
          <PanelList>
            {activity.map((event) => {
              const isExpense = event.kind === "expense";
              const Icon = isExpense ? getCategory(event.expense.category).icon : CheckCircle2;

              const title = isExpense
                ? event.expense.description
                : `${userById(event.settlement.fromUser).name} paid ${userById(event.settlement.toUser).name}`;

              const meta = isExpense
                ? `${event.expense.paidBy === currentUser?.id ? "You" : userById(event.expense.paidBy).name} paid · ${groupName(event.groupId)}`
                : `Settled up · ${groupName(event.groupId)}`;

              return (
                <li key={`${event.kind}-${event.id}`}>
                  <Link
                    to="/group/$groupId"
                    params={{ groupId: event.groupId }}
                    className="row-hover flex items-center gap-3 p-4"
                  >
                    <span
                      className={
                        isExpense
                          ? "grid size-9 shrink-0 place-items-center rounded-lg border border-border bg-surface text-muted-foreground"
                          : "grid size-9 shrink-0 place-items-center rounded-lg border border-border bg-positive-soft text-positive"
                      }
                      aria-hidden
                    >
                      <Icon className="size-4" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium">{title}</p>
                      <p className="truncate text-xs text-muted-foreground">{meta}</p>
                    </div>
                    <div className="shrink-0 text-right">
                      <Money
                        value={isExpense ? event.expense.totalAmount : event.settlement.amount}
                        size="sm"
                        tone={isExpense ? "plain" : "positive"}
                      />
                      <p className="text-xs text-muted-foreground">
                        {formatRelativeDate(event.at)}
                      </p>
                    </div>
                  </Link>
                </li>
              );
            })}
          </PanelList>
        </Panel>
      )}
    </Section>
  );
}
