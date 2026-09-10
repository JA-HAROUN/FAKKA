import { useState } from "react";
import { ArrowRight, CheckCircle2, Handshake } from "lucide-react";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/common/EmptyState";
import { Money } from "@/components/common/Money";
import { Panel, PanelList, Section } from "@/components/common/Section";
import type { Group, Settlement } from "@/types";
import { useApp } from "@/context/AppContext";
import { formatAmount, type SimplifiedDebt } from "@/utils/calculations";
import { formatRelativeDate } from "@/utils/format";

/**
 * The settlement plan: the fewest transfers that clear the group, phrased from
 * the reader's point of view ("You owe Ahmed" rather than "John → Ahmed").
 * Marking a payment is confirmed first — it moves money in the ledger and
 * cannot be undone from the UI.
 */
export function SettleUpPanel({
  group,
  pending,
  settled,
}: {
  group: Group;
  pending: SimplifiedDebt[];
  settled: Settlement[];
}) {
  const { userById, currentUser, markSettlementPaid } = useApp();
  const [confirming, setConfirming] = useState<SimplifiedDebt | null>(null);

  const nameOf = (id: string) => (id === currentUser?.id ? "You" : userById(id).name);
  const paid = [...settled]
    .filter((s) => s.status === "paid")
    .sort((a, b) => (b.paidAt ?? "").localeCompare(a.paidAt ?? ""));

  function confirmPayment(debt: SimplifiedDebt) {
    markSettlementPaid({
      groupId: group.id,
      fromUser: debt.fromUser,
      toUser: debt.toUser,
      amount: debt.amount,
    });
    setConfirming(null);
    toast.success("Payment recorded", {
      description: `${userById(debt.fromUser).name} → ${userById(debt.toUser).name} · ${formatAmount(debt.amount)}`,
    });
  }

  return (
    <>
      <Section
        title="Settle up"
        description={pending.length > 0 ? "The fewest transfers that clear the group." : undefined}
      >
        {pending.length === 0 ? (
          paid.length > 0 ? (
            <Panel className="flex items-center gap-3">
              <span
                className="grid size-9 shrink-0 place-items-center rounded-lg bg-positive-soft text-positive"
                aria-hidden
              >
                <CheckCircle2 className="size-4" />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-medium">Everyone is settled up</p>
                <p className="text-xs text-muted-foreground">
                  No outstanding transfers in this group.
                </p>
              </div>
            </Panel>
          ) : (
            <EmptyState
              icon={Handshake}
              title="Nothing to settle"
              description="Once expenses are added, Fakka works out who should pay whom."
            />
          )
        ) : (
          <Panel flush>
            <PanelList>
              {pending.map((debt) => {
                const involvesMe =
                  debt.fromUser === currentUser?.id || debt.toUser === currentUser?.id;
                const iOwe = debt.fromUser === currentUser?.id;

                return (
                  <li key={debt.id} className="space-y-2.5 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <p className="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1 text-sm">
                        <span className="font-medium">{nameOf(debt.fromUser)}</span>
                        <ArrowRight
                          className="size-3.5 shrink-0 text-muted-foreground"
                          aria-hidden
                        />
                        <span className="font-medium">{nameOf(debt.toUser)}</span>
                      </p>
                      <Money
                        className="shrink-0"
                        value={debt.amount}
                        tone={involvesMe ? (iOwe ? "negative" : "positive") : "plain"}
                      />
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="flex min-w-0 items-center gap-2">
                        <Badge variant="neutral">Pending</Badge>
                        <span className="min-w-0 truncate text-xs text-muted-foreground">
                          {involvesMe
                            ? iOwe
                              ? "you need to pay this"
                              : "you should receive this"
                            : "between other members"}
                        </span>
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-9 grow sm:grow-0"
                        onClick={() => setConfirming(debt)}
                      >
                        <CheckCircle2 aria-hidden /> Mark as paid
                      </Button>
                    </div>
                  </li>
                );
              })}
            </PanelList>
          </Panel>
        )}
      </Section>

      {paid.length > 0 && (
        <Section title="Settled payments" headingLevel="h3" className="mt-6">
          <Panel flush>
            <PanelList>
              {paid.map((settlement) => (
                <li key={settlement.id} className="flex items-center gap-3 px-4 py-3">
                  <span
                    className="grid size-8 shrink-0 place-items-center rounded-lg bg-positive-soft text-positive"
                    aria-hidden
                  >
                    <CheckCircle2 className="size-4" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm">
                      <span className="font-medium">{nameOf(settlement.fromUser)}</span> paid{" "}
                      <span className="font-medium">{nameOf(settlement.toUser)}</span>
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {settlement.paidAt ? formatRelativeDate(settlement.paidAt) : "Recorded"}
                    </p>
                  </div>
                  <Money value={settlement.amount} size="sm" tone="neutral" />
                </li>
              ))}
            </PanelList>
          </Panel>
        </Section>
      )}

      <AlertDialog open={confirming !== null} onOpenChange={(v) => !v && setConfirming(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Record this payment?</AlertDialogTitle>
            <AlertDialogDescription>
              {confirming && (
                <>
                  {userById(confirming.fromUser).name} paying {userById(confirming.toUser).name}{" "}
                  <strong className="font-semibold text-foreground tabular-nums">
                    {formatAmount(confirming.amount)}
                  </strong>{" "}
                  will be added to the group ledger and balances will update. This can't be undone
                  here.
                </>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => confirming && confirmPayment(confirming)}>
              Yes, mark as paid
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
