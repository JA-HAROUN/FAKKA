import { useState } from "react";
import { Download, FileSpreadsheet, FileText } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Money } from "@/components/common/Money";
import type { Group } from "@/types";
import { useApp } from "@/context/AppContext";
import { buildReportCsv, downloadCsv } from "@/utils/exportCsv";
import { groupBalances, groupTotal, simplifyDebts } from "@/utils/calculations";
import { formatShortDate, pluralize } from "@/utils/format";
import { getCategory } from "@/utils/categories";

/** Group report: preview on screen, or download the CSV the report is built from. */
export function ExportButton({ group }: { group: Group }) {
  const { users, userById, expensesOfGroup, settlementsOfGroup } = useApp();
  const [open, setOpen] = useState(false);
  const expenses = expensesOfGroup(group.id);
  const settlements = settlementsOfGroup(group.id);
  const balances = groupBalances(group.members, expenses, settlements);
  const pending = simplifyDebts(balances);
  const paid = settlements.filter((s) => s.status === "paid");

  function exportCsvNow() {
    const csv = buildReportCsv({ group, users, expenses, settlements });
    downloadCsv(`fakka-${group.name.toLowerCase().replace(/\s+/g, "-")}.csv`, csv);
    toast.success("CSV report downloaded");
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline">
            <Download aria-hidden /> Report
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-52">
          <DropdownMenuItem onSelect={() => setOpen(true)}>
            <FileText aria-hidden /> Preview report
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={exportCsvNow}>
            <FileSpreadsheet aria-hidden /> Download CSV
          </DropdownMenuItem>
          <DropdownMenuItem
            onSelect={() => toast.info("PDF export is coming soon — CSV is available now.")}
          >
            <FileText aria-hidden /> Download PDF
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>{group.name} — expense report</DialogTitle>
            <DialogDescription>
              {pluralize(group.members.length, "member")} ·{" "}
              {group.members.map((id) => userById(id).name).join(", ")}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-6">
            <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-border bg-border sm:grid-cols-3">
              <div className="bg-card p-3">
                <dt className="text-xs font-medium text-muted-foreground">Total spending</dt>
                <dd className="mt-1">
                  <Money value={groupTotal(expenses)} />
                </dd>
              </div>
              <div className="bg-card p-3">
                <dt className="text-xs font-medium text-muted-foreground">Expenses</dt>
                <dd className="mt-1 text-sm font-semibold tabular-nums">{expenses.length}</dd>
              </div>
              <div className="bg-card p-3">
                <dt className="text-xs font-medium text-muted-foreground">Outstanding transfers</dt>
                <dd className="mt-1 text-sm font-semibold tabular-nums">{pending.length}</dd>
              </div>
            </dl>

            <section className="space-y-2">
              <h3 className="section-label">Expenses</h3>
              <div className="overflow-hidden rounded-lg border border-border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Date</TableHead>
                      <TableHead>Description</TableHead>
                      <TableHead className="hidden sm:table-cell">Category</TableHead>
                      <TableHead>Paid by</TableHead>
                      <TableHead className="text-right">Amount</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {expenses.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} className="text-center text-muted-foreground">
                          No expenses recorded.
                        </TableCell>
                      </TableRow>
                    )}
                    {[...expenses]
                      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
                      .map((expense) => (
                        <TableRow key={expense.id}>
                          <TableCell className="whitespace-nowrap text-muted-foreground">
                            {formatShortDate(expense.createdAt)}
                          </TableCell>
                          <TableCell className="font-medium">{expense.description}</TableCell>
                          <TableCell className="hidden sm:table-cell text-muted-foreground">
                            {getCategory(expense.category).label}
                          </TableCell>
                          <TableCell className="text-muted-foreground">
                            {userById(expense.paidBy).name}
                          </TableCell>
                          <TableCell className="text-right">
                            <Money value={expense.totalAmount} size="sm" />
                          </TableCell>
                        </TableRow>
                      ))}
                  </TableBody>
                </Table>
              </div>
            </section>

            <div className="grid gap-6 sm:grid-cols-2">
              <section className="space-y-2">
                <h3 className="section-label">Balances</h3>
                <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
                  {group.members.map((id) => {
                    const balance = balances[id] ?? 0;
                    return (
                      <li
                        key={id}
                        className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                      >
                        <span className="min-w-0 truncate">{userById(id).name}</span>
                        <Money value={balance} size="sm" tone="auto" signed />
                      </li>
                    );
                  })}
                </ul>
              </section>

              <section className="space-y-2">
                <h3 className="section-label">Settlements</h3>
                <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
                  {paid.map((settlement) => (
                    <li
                      key={settlement.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <span className="min-w-0 truncate">
                        {userById(settlement.fromUser).name} → {userById(settlement.toUser).name}
                      </span>
                      <span className="flex shrink-0 items-center gap-2">
                        <Money value={settlement.amount} size="sm" tone="neutral" />
                        <Badge variant="positive">Paid</Badge>
                      </span>
                    </li>
                  ))}
                  {pending.map((debt) => (
                    <li
                      key={debt.id}
                      className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
                    >
                      <span className="min-w-0 truncate">
                        {userById(debt.fromUser).name} → {userById(debt.toUser).name}
                      </span>
                      <span className="flex shrink-0 items-center gap-2">
                        <Money value={debt.amount} size="sm" />
                        <Badge variant="neutral">Pending</Badge>
                      </span>
                    </li>
                  ))}
                  {pending.length === 0 && paid.length === 0 && (
                    <li className="px-3 py-2 text-sm text-muted-foreground">Nothing to settle.</li>
                  )}
                </ul>
              </section>
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => toast.info("PDF export is coming soon — CSV is available now.")}
            >
              <FileText aria-hidden /> Export PDF
            </Button>
            <Button onClick={exportCsvNow}>
              <FileSpreadsheet aria-hidden /> Export CSV
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
