import { useMemo } from "react";
import { useApp } from "@/context/AppContext";
import {
  counterpartyBalances,
  overallPosition,
  recentActivity,
  summarizeGroup,
  type ActivityEvent,
  type CounterpartyBalance,
  type GroupSummary,
  type OverallPosition,
} from "@/utils/insights";

/**
 * Everything the dashboard and friends page read, derived once from the app
 * state. Presentation components stay free of balance arithmetic.
 */
export function useGroupSummaries(): {
  summaries: GroupSummary[];
  position: OverallPosition;
  people: CounterpartyBalance[];
  activity: ActivityEvent[];
} {
  const { groups, expenses, settlements, currentUser } = useApp();

  return useMemo(() => {
    if (!currentUser) {
      return { summaries: [], position: { owed: 0, owing: 0, net: 0 }, people: [], activity: [] };
    }

    const myGroups = groups.filter((g) => g.members.includes(currentUser.id));
    const summaries = myGroups
      .map((group) =>
        summarizeGroup(
          group,
          expenses.filter((e) => e.groupId === group.id),
          settlements.filter((s) => s.groupId === group.id),
          currentUser.id,
        ),
      )
      .sort((a, b) => b.lastActivity.localeCompare(a.lastActivity));

    return {
      summaries,
      position: overallPosition(summaries),
      people: counterpartyBalances(summaries, currentUser.id),
      activity: recentActivity(expenses, settlements, new Set(myGroups.map((g) => g.id))),
    };
  }, [groups, expenses, settlements, currentUser]);
}
