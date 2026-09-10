import type { Expense, Group, Settlement, User } from "@/types";
import { groupBalances, groupTotal, round2, simplifyDebts } from "./calculations";

function escape(value: string | number): string {
  const s = String(value);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function row(cells: (string | number)[]): string {
  return cells.map(escape).join(",");
}

export interface ReportInput {
  group: Group;
  users: User[];
  expenses: Expense[];
  settlements: Settlement[];
}

export function buildReportCsv({ group, users, expenses, settlements }: ReportInput): string {
  const nameOf = (id: string) => users.find((u) => u.id === id)?.name ?? id;
  const lines: string[] = [];

  lines.push(row(["Fakka report"]));
  lines.push(row(["Group", group.name]));
  lines.push(row(["Created", new Date(group.createdAt).toLocaleDateString()]));
  lines.push(row(["Members", group.members.map(nameOf).join(" | ")]));
  lines.push(row(["Total expenses (EGP)", groupTotal(expenses)]));
  lines.push("");

  lines.push(row(["Date", "Category", "Description", "Paid by", "Total (EGP)", "Split", "Shares"]));
  for (const e of [...expenses].sort((a, b) => a.createdAt.localeCompare(b.createdAt))) {
    lines.push(
      row([
        new Date(e.createdAt).toLocaleDateString(),
        e.category,
        e.description,
        nameOf(e.paidBy),
        e.totalAmount,
        e.splitType,
        Object.entries(e.shares)
          .map(([uid, amt]) => `${nameOf(uid)}: ${round2(amt)}`)
          .join(" | "),
      ]),
    );
  }
  lines.push("");

  const balances = groupBalances(group.members, expenses, settlements);
  lines.push(row(["Member", "Balance (EGP)", "Status"]));
  for (const id of group.members) {
    const b = round2(balances[id] ?? 0);
    lines.push(row([nameOf(id), b, b > 0 ? "is owed" : b < 0 ? "owes" : "settled"]));
  }
  lines.push("");

  lines.push(row(["From", "To", "Amount (EGP)", "Status"]));
  for (const s of settlements.filter((s) => s.status === "paid")) {
    lines.push(row([nameOf(s.fromUser), nameOf(s.toUser), s.amount, "paid"]));
  }
  for (const d of simplifyDebts(balances)) {
    lines.push(row([nameOf(d.fromUser), nameOf(d.toUser), d.amount, "pending"]));
  }

  return lines.join("\n");
}

export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
