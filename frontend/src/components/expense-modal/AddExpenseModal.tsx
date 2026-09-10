import { useState } from "react";
import { Plus, ArrowLeft } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { UserAvatar } from "@/components/common/UserAvatar";
import { useApp } from "@/context/AppContext";
import type { ExpenseDraft, Group } from "@/types";
import { getCategory } from "@/utils/categories";
import { equalShares, formatAmount } from "@/utils/calculations";
import { ManualExpenseForm, draftError } from "./ManualExpenseForm";
import { AiExpenseInput } from "./AiExpenseInput";

type Method = "manual" | "ai";

export function AddExpenseModal({ group }: { group: Group }) {
  const { userById, currentUser, addExpense } = useApp();
  const members = group.members.map(userById);
  const payer = currentUser?.id ?? group.members[0] ?? "";

  const emptyDraft = (): ExpenseDraft => ({
    category: "food",
    description: "",
    totalAmount: 0,
    paidBy: payer,
    participants: [...group.members],
    splitType: "equal",
    shares: equalShares(0, group.members),
    source: "manual",
  });

  const [open, setOpen] = useState(false);
  const [method, setMethod] = useState<Method>("manual");
  const [draft, setDraft] = useState<ExpenseDraft>(emptyDraft);
  const [reviewing, setReviewing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setDraft(emptyDraft());
    setReviewing(false);
    setError(null);
    setMethod("manual");
  }

  function goToReview(next: ExpenseDraft) {
    const problem = draftError(next);
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    setDraft(next);
    setReviewing(true);
  }

  const category = getCategory(draft.category);

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        setOpen(v);
        if (!v) reset();
      }}
    >
      <DialogTrigger asChild>
        <Button size="lg" className="rounded-full">
          <Plus className="size-4" /> Add expense
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{reviewing ? "Review expense" : "Add an expense"}</DialogTitle>
          <DialogDescription>
            {reviewing
              ? "Check the details, then save it to the group."
              : "Type it in, or describe it in plain language."}
          </DialogDescription>
        </DialogHeader>

        {reviewing ? (
          <div className="space-y-4">
            <div className="card-soft space-y-3 p-4">
              <div className="flex items-center gap-3">
                <span className="grid size-11 place-items-center rounded-xl bg-primary-soft">
                  <category.icon className="size-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{draft.description}</p>
                  <p className="text-xs text-muted-foreground">
                    {category.label} · paid by {userById(draft.paidBy).name}
                  </p>
                </div>
                <p className="font-bold tabular-nums">{formatAmount(draft.totalAmount)}</p>
              </div>

              <ul className="divide-y divide-border border-t border-border pt-2">
                {draft.participants.map((id) => (
                  <li key={id} className="flex items-center gap-3 py-2">
                    <UserAvatar user={userById(id)} size="sm" />
                    <span className="flex-1 truncate text-sm font-medium">
                      {userById(id).name}
                    </span>
                    <span className="text-sm tabular-nums text-muted-foreground">
                      {formatAmount(draft.shares[id] ?? 0)}
                    </span>
                  </li>
                ))}
              </ul>

              {draft.items && draft.items.length > 0 && (
                <div className="rounded-xl bg-secondary p-3">
                  <p className="text-xs font-semibold text-muted-foreground">Scanned items</p>
                  <ul className="mt-1 space-y-1 text-sm">
                    {draft.items.map((item) => (
                      <li key={item.id} className="flex justify-between gap-3">
                        <span className="truncate">
                          {item.quantity}× {item.name}
                        </span>
                        <span className="tabular-nums">
                          {formatAmount(item.price * item.quantity)}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            <div className="flex flex-wrap justify-end gap-2">
              <Button variant="ghost" onClick={() => setReviewing(false)}>
                <ArrowLeft className="size-4" /> Back to edit
              </Button>
              <Button
                onClick={() => {
                  addExpense(group.id, draft);
                  setOpen(false);
                  reset();
                  toast.success("Expense added");
                }}
              >
                Save expense
              </Button>
            </div>
          </div>
        ) : (
          <Tabs value={method} onValueChange={(v) => setMethod(v as Method)}>
            <TabsList className="w-full">
              <TabsTrigger value="manual" className="flex-1">
                Manual
              </TabsTrigger>
              <TabsTrigger value="ai" className="flex-1">
                Describe it
              </TabsTrigger>
            </TabsList>

            <TabsContent value="manual" className="mt-4">
              <ManualExpenseForm
                members={members}
                draft={draft}
                setDraft={(d) => {
                  setDraft(d);
                  setError(null);
                }}
                onContinue={() => goToReview(draft)}
                error={error}
              />
            </TabsContent>

            <TabsContent value="ai" className="mt-4">
              <AiExpenseInput
                members={members}
                fallbackPayer={payer}
                onParsed={(d) => {
                  setDraft(d);
                  setMethod("manual");
                  setError(null);
                }}
                onSwitchToManual={() => setMethod("manual")}
              />
            </TabsContent>

          </Tabs>
        )}
      </DialogContent>
    </Dialog>
  );
}
