import { useState } from "react";
import { ArrowLeft, Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Money } from "@/components/common/Money";
import { ResponsiveModal } from "@/components/common/ResponsiveModal";
import { UserAvatar } from "@/components/common/UserAvatar";
import { useApp } from "@/context/AppContext";
import type { ExpenseDraft, Group } from "@/types";
import { getCategory } from "@/utils/categories";
import { equalShares, formatAmount } from "@/utils/calculations";
import { formatNumber, pluralize } from "@/utils/format";
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

  function save() {
    addExpense(group.id, draft);
    setOpen(false);
    reset();
    toast.success("Expense added", {
      description: `${draft.description} · ${formatAmount(draft.totalAmount)}`,
    });
  }

  const category = getCategory(draft.category);

  return (
    <ResponsiveModal
      open={open}
      onOpenChange={(v) => {
        setOpen(v);
        if (!v) reset();
      }}
      trigger={
        <Button>
          <Plus aria-hidden /> Add expense
        </Button>
      }
      className="sm:max-w-2xl"
      title={reviewing ? "Review expense" : "Add an expense"}
      description={
        reviewing
          ? "Check the details before it's saved to the group."
          : `Splitting between ${pluralize(members.length, "member")} of ${group.name}.`
      }
      footer={
        reviewing ? (
          <>
            <Button variant="ghost" onClick={() => setReviewing(false)}>
              <ArrowLeft aria-hidden /> Back to edit
            </Button>
            <Button onClick={save}>Save expense</Button>
          </>
        ) : undefined
      }
    >
      {reviewing ? (
        <div className="space-y-4">
          <div className="panel-inset divide-y divide-border">
            <div className="flex items-center gap-3 p-4">
              <span
                className="grid size-10 shrink-0 place-items-center rounded-md border border-border bg-card text-muted-foreground"
                aria-hidden
              >
                <category.icon className="size-4" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-body font-semibold">{draft.description}</p>
                <p className="truncate text-caption text-muted-foreground">
                  {category.label} · paid by {userById(draft.paidBy).name}
                </p>
              </div>
              {/* The figure being committed: the loudest thing in the review step. */}
              <Money value={draft.totalAmount} size="xl" />
            </div>

            <div className="p-4">
              <p className="eyebrow">
                Split {draft.splitType === "equal" ? "equally" : "by custom amounts"} between{" "}
                {pluralize(draft.participants.length, "person", "people")}
              </p>
              <ul className="mt-3 space-y-2">
                {draft.participants.map((id) => (
                  <li key={id} className="flex items-center gap-3">
                    <UserAvatar user={userById(id)} size="xs" />
                    <span className="min-w-0 flex-1 truncate text-label">
                      {userById(id).name}
                      {id === currentUser?.id && (
                        <span className="ml-1.5 text-caption text-muted-foreground">you</span>
                      )}
                    </span>
                    <span className="text-label font-semibold tabular-nums">
                      {formatNumber(draft.shares[id] ?? 0)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            {draft.items && draft.items.length > 0 && (
              <div className="p-4">
                <p className="eyebrow">Items recorded ({draft.items.length})</p>
                <ul className="mt-3 space-y-1.5 text-label">
                  {draft.items.map((item) => (
                    <li key={item.id} className="flex justify-between gap-3">
                      <span className="min-w-0 truncate">
                        {item.quantity}× {item.name || "Unnamed item"}
                      </span>
                      <span className="shrink-0 tabular-nums">
                        {formatNumber(item.price * item.quantity)}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {draft.source !== "manual" && (
            <p className="text-caption text-muted-foreground">
              Filled in from your description — edit anything that looks wrong before saving.
            </p>
          )}
        </div>
      ) : (
        <Tabs value={method} onValueChange={(v) => setMethod(v as Method)}>
          <TabsList className="w-full">
            <TabsTrigger value="manual" className="flex-1">
              Enter manually
            </TabsTrigger>
            <TabsTrigger value="ai" className="flex-1">
              Describe it
            </TabsTrigger>
          </TabsList>

          <TabsContent value="manual">
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

          <TabsContent value="ai">
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
    </ResponsiveModal>
  );
}
