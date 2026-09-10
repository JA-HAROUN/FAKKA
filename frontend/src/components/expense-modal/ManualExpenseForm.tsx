import { ImagePlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { CategoryId, ExpenseDraft, User } from "@/types";
import { CATEGORIES, getCategory } from "@/utils/categories";
import { CURRENCY, equalShares, formatAmount, round2 } from "@/utils/calculations";
import { ReceiptItemsField } from "./ReceiptItemsField";
import { cn } from "@/lib/utils";

export function customSplitError(draft: ExpenseDraft): string | null {
  if (draft.splitType !== "custom") return null;
  const sum = round2(draft.participants.reduce((s, id) => s + (draft.shares[id] ?? 0), 0));
  if (Math.abs(sum - round2(draft.totalAmount)) > 0.01) {
    return `Shares add up to ${formatAmount(sum)} but the total is ${formatAmount(draft.totalAmount)}.`;
  }
  return null;
}

export function draftError(draft: ExpenseDraft): string | null {
  if (!draft.description.trim()) return "Add a short description.";
  if (!(draft.totalAmount > 0)) return "Enter an amount greater than zero.";
  if (!draft.paidBy) return "Select who paid.";
  if (draft.participants.length === 0) return "Pick at least one person to split with.";
  return customSplitError(draft);
}

export function ManualExpenseForm({
  members,
  draft,
  setDraft,
  onContinue,
  error,
}: {
  members: User[];
  draft: ExpenseDraft;
  setDraft: (d: ExpenseDraft) => void;
  onContinue: () => void;
  error: string | null;
}) {
  const update = (patch: Partial<ExpenseDraft>) => {
    const next = { ...draft, ...patch };
    if (next.splitType === "equal") next.shares = equalShares(next.totalAmount, next.participants);
    setDraft(next);
  };

  const toggleParticipant = (id: string, checked: boolean) => {
    const participants = checked
      ? [...draft.participants, id]
      : draft.participants.filter((p) => p !== id);
    const shares = { ...draft.shares };
    if (!checked) delete shares[id];
    update({ participants, shares });
  };

  const setCustomShare = (id: string, value: number) => {
    const shares = { ...draft.shares, [id]: value };
    const participants = members.filter((m) => (shares[m.id] ?? 0) > 0).map((m) => m.id);
    setDraft({ ...draft, shares, participants });
  };

  const splitError = customSplitError(draft);

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label>Category</Label>
          <Select
            value={draft.category}
            onValueChange={(v) => update({ category: v as CategoryId })}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {CATEGORIES.map((c) => (
                <SelectItem key={c.id} value={c.id}>
                  <c.icon className="size-4" />
                  {c.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="amount">Total amount ({CURRENCY})</Label>
          <Input
            id="amount"
            type="number"
            min={0}
            step="0.01"
            value={draft.totalAmount || ""}
            onChange={(e) => update({ totalAmount: Number(e.target.value) || 0 })}
            placeholder="0.00"
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">Description</Label>
        <Input
          id="description"
          value={draft.description}
          onChange={(e) => update({ description: e.target.value })}
          placeholder="Dinner at Zooba"
        />
      </div>

      {/* Step 1 — who paid */}
      <div className="space-y-2">
        <Label>1. Who paid?</Label>
        <RadioGroup
          value={draft.paidBy}
          onValueChange={(v) => update({ paidBy: v })}
          className="grid gap-1 sm:grid-cols-2"
        >
          {members.map((m) => (
            <label
              key={m.id}
              className="flex cursor-pointer items-center gap-3 rounded-xl px-2 py-2 hover:bg-secondary"
            >
              <RadioGroupItem value={m.id} />
              <span className="text-base">{m.avatar}</span>
              <span className="truncate text-sm font-medium">{m.name}</span>
            </label>
          ))}
        </RadioGroup>
      </div>

      {/* Step 2 — how is it split */}
      <div className="space-y-3">
        <Label>2. Split it equally?</Label>
        <div className="inline-flex rounded-full bg-secondary p-1">
          {(
            [
              { value: "equal", label: "Yes, equally" },
              { value: "custom", label: "No, set amounts" },
            ] as const
          ).map((t) => (
            <button
              key={t.value}
              type="button"
              onClick={() =>
                update({
                  splitType: t.value,
                  shares:
                    t.value === "equal"
                      ? equalShares(draft.totalAmount, draft.participants)
                      : draft.shares,
                })
              }
              className={cn(
                "rounded-full px-4 py-1.5 text-sm font-medium transition-colors",
                draft.splitType === t.value
                  ? "bg-card text-foreground shadow-[var(--shadow-soft)]"
                  : "text-muted-foreground",
              )}
            >
              {t.label}
            </button>
          ))}
        </div>

        {draft.splitType === "equal" ? (
          <div className="space-y-2">
            <p className="text-sm text-muted-foreground">Tick everyone it's split among.</p>
            <ul className="divide-y divide-border rounded-xl border border-border">
              {members.map((m) => {
                const checked = draft.participants.includes(m.id);
                return (
                  <li key={m.id} className="flex items-center gap-3 px-3 py-2.5">
                    <Checkbox
                      checked={checked}
                      onCheckedChange={(v) => toggleParticipant(m.id, Boolean(v))}
                    />
                    <span className="text-base">{m.avatar}</span>
                    <span className="min-w-0 flex-1 truncate text-sm font-medium">{m.name}</span>
                    {checked && (
                      <span className="text-sm font-semibold tabular-nums">
                        {formatAmount(draft.shares[m.id] ?? 0)}
                      </span>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">
              Write what each person owes — leave someone at zero to leave them out.
            </p>
            <ul className="divide-y divide-border rounded-xl border border-border">
              {members.map((m) => (
                <li key={m.id} className="flex items-center gap-3 px-3 py-2.5">
                  <span className="text-base">{m.avatar}</span>
                  <span className="min-w-0 flex-1 truncate text-sm font-medium">{m.name}</span>
                  <Input
                    type="number"
                    min={0}
                    step="0.01"
                    className="h-9 w-28"
                    value={draft.shares[m.id] ?? ""}
                    onChange={(e) => setCustomShare(m.id, Number(e.target.value) || 0)}
                    placeholder="0.00"
                  />
                </li>
              ))}
            </ul>

            <div className="flex items-center justify-between rounded-xl bg-secondary p-3 text-sm">
              <span className="text-muted-foreground">Reference total</span>
              <span className="font-semibold tabular-nums">{formatAmount(draft.totalAmount)}</span>
            </div>
            <div className="flex items-center justify-between rounded-xl bg-secondary p-3 text-sm">
              <span className="text-muted-foreground">Assigned so far</span>
              <span className="font-semibold tabular-nums">
                {formatAmount(
                  round2(members.reduce((sum, m) => sum + (draft.shares[m.id] ?? 0), 0)),
                )}
              </span>
            </div>
            <div
              className={cn(
                "flex items-center justify-between rounded-xl p-3 text-sm",
                Math.abs(round2(draft.totalAmount - members.reduce((sum, m) => sum + (draft.shares[m.id] ?? 0), 0))) <= 0.01
                  ? "bg-positive/10 text-positive"
                  : "bg-negative/10 text-negative",
              )}
            >
              <span className="font-medium">
                {Math.abs(round2(draft.totalAmount - members.reduce((sum, m) => sum + (draft.shares[m.id] ?? 0), 0))) <= 0.01
                  ? "All assigned"
                  : round2(draft.totalAmount - members.reduce((sum, m) => sum + (draft.shares[m.id] ?? 0), 0)) > 0
                    ? "Remaining to assign"
                    : "Over the total"}
              </span>
              <span className="font-bold tabular-nums">
                {formatAmount(
                  Math.abs(round2(draft.totalAmount - members.reduce((sum, m) => sum + (draft.shares[m.id] ?? 0), 0))),
                )}
              </span>
            </div>
          </div>
        )}

        {splitError && <p className="text-sm font-medium text-negative">{splitError}</p>}
      </div>

      {/* Optional extras */}
      <div className="space-y-3">
        <Label>Optional</Label>
        <ReceiptItemsField
          items={draft.items ?? []}
          onChange={(items) => setDraft({ ...draft, items })}
        />

        <label
          htmlFor="receipt-image"
          className="flex cursor-pointer items-center gap-3 rounded-xl border border-dashed border-border px-4 py-3 text-sm text-muted-foreground hover:bg-secondary"
        >
          <ImagePlus className="size-4" />
          {draft.image ? "Photo attached — tap to replace" : "Attach a photo of the bill"}
        </label>
        <input
          id="receipt-image"
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) update({ image: URL.createObjectURL(file) });
          }}
        />
        {draft.image && (
          <img
            src={draft.image}
            alt="Attached receipt"
            className="h-28 w-auto rounded-xl border border-border object-cover"
          />
        )}
      </div>

      {error && !splitError && <p className="text-sm font-medium text-negative">{error}</p>}

      <div className="flex items-center justify-between gap-3 border-t border-border pt-4">
        <p className="text-sm text-muted-foreground">
          {getCategory(draft.category).label} · {draft.participants.length} people
        </p>
        <Button onClick={onContinue}>Review expense</Button>
      </div>
    </div>
  );
}
