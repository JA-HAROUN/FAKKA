import type { Expense, PurchasedItem, Settlement } from "@/types";

export const CURRENCY = "EGP";

export function round2(n: number): number {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

export function formatAmount(n: number): string {
  const abs = Math.abs(round2(n));
  return `${abs.toLocaleString("en-US", { maximumFractionDigits: 2 })} ${CURRENCY}`;
}

export function formatSigned(n: number): string {
  const v = round2(n);
  if (Math.abs(v) < 0.01) return `0 ${CURRENCY}`;
  return `${v > 0 ? "+" : "−"}${formatAmount(v)}`;
}

export type BalanceTone = "positive" | "negative" | "neutral";

export function toneOf(n: number): BalanceTone {
  if (round2(n) > 0.005) return "positive";
  if (round2(n) < -0.005) return "negative";
  return "neutral";
}

export function balanceLabel(n: number): string {
  const tone = toneOf(n);
  if (tone === "positive") return "You are owed";
  if (tone === "negative") return "You owe";
  return "Settled";
}

/** Equal split of `total` across `participants`, remainder on the first participant. */
export function equalShares(total: number, participants: string[]): Record<string, number> {
  const shares: Record<string, number> = {};
  if (participants.length === 0) return shares;
  const base = Math.floor((total * 100) / participants.length) / 100;
  let assigned = 0;
  participants.forEach((id, i) => {
    if (i === participants.length - 1) {
      shares[id] = round2(total - assigned);
    } else {
      shares[id] = base;
      assigned = round2(assigned + base);
    }
  });
  return shares;
}

/** Shares derived from itemized receipt lines (each item split across its assignees). */
export function sharesFromItems(items: PurchasedItem[]): Record<string, number> {
  const shares: Record<string, number> = {};
  for (const item of items) {
    const line = round2(item.price * item.quantity);
    if (item.assignedTo.length === 0) continue;
    const per = line / item.assignedTo.length;
    for (const uid of item.assignedTo) {
      shares[uid] = round2((shares[uid] ?? 0) + per);
    }
  }
  return shares;
}

export function itemsTotal(items: PurchasedItem[]): number {
  return round2(items.reduce((sum, i) => sum + i.price * i.quantity, 0));
}

/**
 * Net balance per member for a group.
 * Positive = the member is owed money. Negative = the member owes money.
 * Paid settlements are applied as real transfers.
 */
export function groupBalances(
  memberIds: string[],
  expenses: Expense[],
  settlements: Settlement[],
): Record<string, number> {
  const balances: Record<string, number> = {};
  memberIds.forEach((id) => (balances[id] = 0));

  for (const e of expenses) {
    balances[e.paidBy] = round2((balances[e.paidBy] ?? 0) + e.totalAmount);
    for (const [uid, amount] of Object.entries(e.shares)) {
      balances[uid] = round2((balances[uid] ?? 0) - amount);
    }
  }

  for (const s of settlements) {
    if (s.status !== "paid") continue;
    balances[s.fromUser] = round2((balances[s.fromUser] ?? 0) + s.amount);
    balances[s.toUser] = round2((balances[s.toUser] ?? 0) - s.amount);
  }

  return balances;
}

export interface SimplifiedDebt {
  id: string;
  fromUser: string;
  toUser: string;
  amount: number;
}

/** Greedy debt simplification: fewest transfers that settle everyone up. */
export function simplifyDebts(balances: Record<string, number>): SimplifiedDebt[] {
  const debtors = Object.entries(balances)
    .filter(([, v]) => v < -0.005)
    .map(([id, v]) => ({ id, amount: -v }))
    .sort((a, b) => b.amount - a.amount);
  const creditors = Object.entries(balances)
    .filter(([, v]) => v > 0.005)
    .map(([id, v]) => ({ id, amount: v }))
    .sort((a, b) => b.amount - a.amount);

  const result: SimplifiedDebt[] = [];
  let i = 0;
  let j = 0;
  while (i < debtors.length && j < creditors.length) {
    const debtor = debtors[i];
    const creditor = creditors[j];
    if (!debtor || !creditor) break;
    const pay = round2(Math.min(debtor.amount, creditor.amount));
    if (pay > 0.005) {
      result.push({
        id: `${debtor.id}->${creditor.id}`,
        fromUser: debtor.id,
        toUser: creditor.id,
        amount: pay,
      });
    }
    debtor.amount = round2(debtor.amount - pay);
    creditor.amount = round2(creditor.amount - pay);
    if (debtor.amount <= 0.005) i++;
    if (creditor.amount <= 0.005) j++;
  }
  return result;
}

export function groupTotal(expenses: Expense[]): number {
  return round2(expenses.reduce((sum, e) => sum + e.totalAmount, 0));
}
