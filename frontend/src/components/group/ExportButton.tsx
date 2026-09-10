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
import type { Group } from "@/types";
import { useApp } from "@/context/AppContext";
import { buildReportCsv, downloadCsv } from "@/utils/exportCsv";
import {
  formatAmount,
  formatSigned,
  groupBalances,
  groupTotal,
  simplifyDebts,
} from "@/utils/calculations";

export function ExportButton({ group }: { group: Group }) {
  const { users, userById, expensesOfGroup, settlementsOfGroup } = useApp();
  const [open, setOpen] = useState(false);
  const expenses = expensesOfGroup(group.id);
  const settlements = settlementsOfGroup(group.id);
  const balances = groupBalances(group.members, expenses, settlements);
  const pending = simplifyDebts(balances);

  function exportCsvNow() {
    const csv = buildReportCsv({ group, users, expenses, settlements });
    downloadCsv(`splitease-${group.name.toLowerCase().replace(/\s+/g, "-")}.csv`, csv);
    toast.success("CSV report downloaded");
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" className="rounded-full">
            <Download className="size-4" /> Export report
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => setOpen(true)}>
            <FileText className="size-4" /> Preview report
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={exportCsvNow}>
            <FileSpreadsheet className="size-4" /> Download CSV
          </DropdownMenuItem>
          <DropdownMenuItem
            onSelect={() => toast.info("PDF export is coming soon — CSV is available now.")}
          >
            <FileText className="size-4" /> Download PDF
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Report preview — {group.name}</DialogTitle>
            <DialogDescription>
              Members: {group.members.map((id) => userById(id).name).join(", ")}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-5 text-sm">
            <div className="rounded-xl bg-secondary p-3">
              <span className="text-muted-foreground">Total expenses</span>{" "}
              <strong className="tabular-nums">{formatAmount(groupTotal(expenses))}</strong>
            </div>

            <div>
              <h4 className="mb-2 font-semibold">Expenses</h4>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="text-muted-foreground">
                    <tr>
                      <th className="py-1 pr-3">Date</th>
                      <th className="py-1 pr-3">Description</th>
                      <th className="py-1 pr-3">Category</th>
                      <th className="py-1 pr-3">Paid by</th>
                      <th className="py-1 text-right">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {expenses.map((e) => (
                      <tr key={e.id} className="border-t border-border">
                        <td className="py-1.5 pr-3">
                          {new Date(e.createdAt).toLocaleDateString()}
                        </td>
                        <td className="py-1.5 pr-3">{e.description}</td>
                        <td className="py-1.5 pr-3 capitalize">{e.category}</td>
                        <td className="py-1.5 pr-3">{userById(e.paidBy).name}</td>
                        <td className="py-1.5 text-right tabular-nums">
                          {formatAmount(e.totalAmount)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div>
              <h4 className="mb-2 font-semibold">Balances</h4>
              <ul className="space-y-1">
                {group.members.map((id) => (
                  <li key={id} className="flex justify-between">
                    <span>{userById(id).name}</span>
                    <span className="tabular-nums">{formatSigned(balances[id] ?? 0)}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <h4 className="mb-2 font-semibold">Settlements</h4>
              <ul className="space-y-1">
                {settlements
                  .filter((s) => s.status === "paid")
                  .map((s) => (
                    <li key={s.id} className="flex justify-between">
                      <span>
                        {userById(s.fromUser).name} → {userById(s.toUser).name}
                      </span>
                      <span className="tabular-nums text-positive">
                        {formatAmount(s.amount)} · paid
                      </span>
                    </li>
                  ))}
                {pending.map((d) => (
                  <li key={d.id} className="flex justify-between">
                    <span>
                      {userById(d.fromUser).name} → {userById(d.toUser).name}
                    </span>
                    <span className="tabular-nums text-negative">
                      {formatAmount(d.amount)} · pending
                    </span>
                  </li>
                ))}
                {pending.length === 0 && settlements.length === 0 && (
                  <li className="text-muted-foreground">Nothing to settle.</li>
                )}
              </ul>
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => toast.info("PDF export is coming soon — CSV is available now.")}
            >
              <FileText className="size-4" /> Export PDF
            </Button>
            <Button onClick={exportCsvNow}>
              <FileSpreadsheet className="size-4" /> Export CSV
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
