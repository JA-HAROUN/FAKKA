import type { Expense, Group, Settlement } from "@/types";
import {
  groupBalances,
  groupTotal,
  round2,
  simplifyDebts,
  type SimplifiedDebt,
} from "./calculations";

/**
 * Read-only views over the existing balance engine, used by the dashboard and
 * the friends page. Nothing here changes how balances are calculated — every
 * figure comes from `groupBalances` / `simplifiedDebts`.
 */

export interface GroupSummary {
  group: Group;
  total: number;
  myBalance: number;
  /** Simplified transfers still outstanding in this group. */
  pending: SimplifiedDebt[];
  /** Most recent expense timestamp, for sorting by activity. */
  lastActivity: string;
}

export function summarizeGroup(
  group: Group,
  expenses: Expense[],
  settlements: Settlement[],
  userId: string,
): GroupSummary {
  const balances = groupBalances(group.members, expenses, settlements);
  const lastExpense = expenses.reduce(
    (latest, e) => (e.createdAt > latest ? e.createdAt : latest),
    group.createdAt,
  );

  return {
    group,
    total: groupTotal(expenses),
    myBalance: round2(balances[userId] ?? 0),
    pending: simplifyDebts(balances),
    lastActivity: lastExpense,
  };
}

export interface OverallPosition {
  /** Total the user is owed across all groups. */
  owed: number;
  /** Total the user owes across all groups. */
  owing: number;
  net: number;
}

export function overallPosition(summaries: GroupSummary[]): OverallPosition {
  let owed = 0;
  let owing = 0;
  for (const s of summaries) {
    if (s.myBalance > 0) owed = round2(owed + s.myBalance);
    else owing = round2(owing - s.myBalance);
  }
  return { owed, owing, net: round2(owed - owing) };
}

export interface CounterpartyBalance {
  userId: string;
  /** Positive = they owe the user. Negative = the user owes them. */
  amount: number;
  /** Groups the balance comes from, so the row can be explained. */
  groupIds: string[];
}

/**
 * Net position per person, aggregated across every group, based on the same
 * simplified transfers shown inside each group.
 */
export function counterpartyBalances(
  summaries: GroupSummary[],
  userId: string,
): CounterpartyBalance[] {
  const byUser = new Map<string, CounterpartyBalance>();

  const add = (otherId: string, amount: number, groupId: string) => {
    const existing = byUser.get(otherId) ?? { userId: otherId, amount: 0, groupIds: [] };
    existing.amount = round2(existing.amount + amount);
    if (!existing.groupIds.includes(groupId)) existing.groupIds.push(groupId);
    byUser.set(otherId, existing);
  };

  for (const summary of summaries) {
    for (const debt of summary.pending) {
      if (debt.toUser === userId) add(debt.fromUser, debt.amount, summary.group.id);
      else if (debt.fromUser === userId) add(debt.toUser, -debt.amount, summary.group.id);
    }
  }

  return [...byUser.values()]
    .filter((b) => Math.abs(b.amount) > 0.005)
    .sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount));
}

export type ActivityEvent =
  | { kind: "expense"; id: string; at: string; groupId: string; expense: Expense }
  | { kind: "settlement"; id: string; at: string; groupId: string; settlement: Settlement };

/** Newest-first feed of expenses and completed settlements across groups. */
export function recentActivity(
  expenses: Expense[],
  settlements: Settlement[],
  groupIds: Set<string>,
  limit = 6,
): ActivityEvent[] {
  const events: ActivityEvent[] = [];

  for (const e of expenses) {
    if (!groupIds.has(e.groupId)) continue;
    events.push({ kind: "expense", id: e.id, at: e.createdAt, groupId: e.groupId, expense: e });
  }
  for (const s of settlements) {
    if (!groupIds.has(s.groupId) || s.status !== "paid" || !s.paidAt) continue;
    events.push({ kind: "settlement", id: s.id, at: s.paidAt, groupId: s.groupId, settlement: s });
  }

  return events.sort((a, b) => b.at.localeCompare(a.at)).slice(0, limit);
}
