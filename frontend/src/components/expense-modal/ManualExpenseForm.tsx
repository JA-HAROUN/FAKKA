import { Field, FormError } from "@/components/common/Field";
import { Money } from "@/components/common/Money";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { useApp } from "@/context/AppContext";
import { cn } from "@/lib/utils";
import type { CategoryId, ExpenseDraft, User } from "@/types";
import { CURRENCY, equalShares, formatAmount, round2 } from "@/utils/calculations";
import { CATEGORIES } from "@/utils/categories";
import { formatNumber, pluralize } from "@/utils/format";
import { ImagePlus, Loader2, Trash2 } from "lucide-react";
import { useState } from "react";
import { ReceiptItemsField } from "./ReceiptItemsField";

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

const SPLIT_OPTIONS = [
  { value: "equal", label: "Split equally" },
  { value: "custom", label: "Custom amounts" },
] as const;

export function ManualExpenseForm({
  groupId,
  members,
  draft,
  setDraft,
  onContinue,
  error,
}: {
  groupId: string;
  members: User[];
  draft: ExpenseDraft;
  setDraft: (d: ExpenseDraft) => void;
  onContinue: () => void;
  error: string | null;
}) {
  const { parseReceipt } = useApp();
  const [ocrLoading, setOcrLoading] = useState(false);
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
  const assigned = round2(members.reduce((sum, m) => sum + (draft.shares[m.id] ?? 0), 0));
  const difference = round2(draft.totalAmount - assigned);
  const balanced = Math.abs(difference) <= 0.01;

  return (
    <div className="space-y-5">
      <Field label="Description" required>
        {(field) => (
          <Input
            {...field}
            value={draft.description}
            onChange={(e) => update({ description: e.target.value })}
            placeholder="Dinner at Zooba"
          />
        )}
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={`Total amount (${CURRENCY})`} required>
          {(field) => (
            <Input
              {...field}
              type="number"
              inputMode="decimal"
              min={0}
              step="0.01"
              value={draft.totalAmount || ""}
              onChange={(e) => update({ totalAmount: Number(e.target.value) || 0 })}
              placeholder="0.00"
              className="tabular-nums"
            />
          )}
        </Field>

        <div className="space-y-1.5">
          <Label>Category</Label>
          <Select
            value={draft.category}
            onValueChange={(v) => update({ category: v as CategoryId })}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {CATEGORIES.map((category) => (
                <SelectItem key={category.id} value={category.id}>
                  <category.icon className="size-4 text-muted-foreground" aria-hidden />
                  {category.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="space-y-1.5">
        <Label>
          Who paid?<span className="text-negative">*</span>
        </Label>
        <Select value={draft.paidBy} onValueChange={(v) => update({ paidBy: v })}>
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Select who paid" />
          </SelectTrigger>
          <SelectContent>
            {members.map((member) => (
              <SelectItem key={member.id} value={member.id}>
                <span aria-hidden>{member.avatar}</span>
                {/* {member.name} */}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Split */}
      <div className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Label>How is it split?</Label>
          <div
            role="radiogroup"
            aria-label="Split method"
            className="inline-flex rounded-lg border border-border bg-surface p-1"
          >
            {SPLIT_OPTIONS.map((option) => {
              const active = draft.splitType === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  onClick={() =>
                    update({
                      splitType: option.value,
                      shares:
                        option.value === "equal"
                          ? equalShares(draft.totalAmount, draft.participants)
                          : draft.shares,
                    })
                  }
                  className={cn(
                    "cursor-pointer rounded-md px-3 py-1.5 text-label font-semibold transition-colors",
                    active
                      ? "bg-card text-foreground shadow-xs"
                      : "text-muted-foreground hover:text-foreground",
                  )}
                >
                  {option.label}
                </button>
              );
            })}
          </div>
        </div>

        {draft.splitType === "equal" ? (
          <div className="space-y-2">
            <div className="flex items-center justify-between gap-3">
              <p className="text-caption text-muted-foreground">
                Everyone ticked below splits it evenly.
              </p>
              <button
                type="button"
                className="cursor-pointer text-caption font-semibold text-primary hover:underline"
                onClick={() => {
                  const all = draft.participants.length === members.length;
                  const participants = all ? [] : members.map((m) => m.id);
                  update({ participants, shares: equalShares(draft.totalAmount, participants) });
                }}
              >
                {draft.participants.length === members.length ? "Clear all" : "Select everyone"}
              </button>
            </div>

            <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
              {members.map((member) => {
                const checked = draft.participants.includes(member.id);
                return (
                  <li key={member.id}>
                    <label className="row-hover flex cursor-pointer items-center gap-3 px-3 py-2.5">
                      <Checkbox
                        checked={checked}
                        onCheckedChange={(v) => toggleParticipant(member.id, Boolean(v))}
                        aria-label={`Include ${member.name}`}
                      />
                      <span className="text-base" aria-hidden>
                        {member.avatar}
                      </span>
                      <span className="min-w-0 flex-1 truncate text-body font-semibold">
                        {/* {member.name} */}
                      </span>
                      {checked && (
                        <span className="text-body font-semibold tabular-nums">
                          {formatNumber(draft.shares[member.id] ?? 0)}
                        </span>
                      )}
                    </label>
                  </li>
                );
              })}
            </ul>
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-caption text-muted-foreground">
              Enter what each person owes. Leave someone at zero to leave them out.
            </p>

            <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
              {members.map((member) => (
                <li key={member.id} className="flex items-center gap-3 px-3 py-2.5">
                  <span className="text-base" aria-hidden>
                    {member.avatar}
                  </span>
                  <span className="min-w-0 flex-1 truncate text-body font-semibold">
                    {member.name}
                  </span>
                  <Input
                    type="number"
                    inputMode="decimal"
                    min={0}
                    step="0.01"
                    className="h-9 w-24 rounded-md text-right tabular-nums"
                    value={draft.shares[member.id] ?? ""}
                    onChange={(e) => setCustomShare(member.id, Number(e.target.value) || 0)}
                    placeholder="0.00"
                    aria-label={`Share for ${member.name}`}
                  />
                </li>
              ))}
            </ul>

            {/* Running reconciliation — the one thing that blocks saving. */}
            <div className="panel-inset divide-y divide-border text-label">
              <div className="flex items-center justify-between px-3 py-2.5">
                <span className="text-muted-foreground">Expense total</span>
                <span className="font-semibold tabular-nums">{formatAmount(draft.totalAmount)}</span>
              </div>
              <div className="flex items-center justify-between px-3 py-2.5">
                <span className="text-muted-foreground">Assigned</span>
                <span className="font-semibold tabular-nums">{formatAmount(assigned)}</span>
              </div>
              <div
                className={cn(
                  "flex items-center justify-between px-3 py-2.5 font-semibold",
                  balanced ? "text-positive" : "text-negative",
                )}
              >
                <span>
                  {balanced
                    ? "Fully assigned"
                    : difference > 0
                      ? "Still to assign"
                      : "Over the total by"}
                </span>
                <span className="tabular-nums">{formatAmount(Math.abs(difference))}</span>
              </div>
            </div>
          </div>
        )}

        <FormError message={splitError} />
      </div>

      {/* Optional extras */}
      <div className="space-y-3 border-t border-border pt-5">
        <div className="space-y-1">
          <p className="section-label">Optional details</p>
          <p className="text-caption text-muted-foreground">
            Record what was bought, or attach a photo of the bill. Neither changes the split.
          </p>
        </div>

        <ReceiptItemsField
          items={draft.items ?? []}
          onChange={(items) => setDraft({ ...draft, items })}
        />

        {draft.image ? (
          <div className="flex items-center gap-3 rounded-lg border border-border p-3">
            <img
              src={draft.image}
              alt="Attached bill"
              className="size-14 rounded-md border border-border object-cover"
            />
            <p className="min-w-0 flex-1 text-label text-muted-foreground">Photo attached</p>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label="Remove photo"
              onClick={() => {
                // `exactOptionalPropertyTypes` is on: drop the key rather than
                // setting it to undefined.
                const { image: _removed, ...rest } = draft;
                setDraft(rest);
              }}
            >
              <Trash2 aria-hidden />
            </Button>
          </div>
        ) : (
          <Label
            htmlFor="receipt-image"
            className="row-hover flex cursor-pointer items-center gap-2.5 rounded-lg border border-dashed border-border px-3 py-3 text-label font-normal text-muted-foreground"
          >
            {ocrLoading ? <Loader2 className="size-4 animate-spin" aria-hidden /> : <ImagePlus className="size-4" aria-hidden />}
            {ocrLoading ? "Reading receipt…" : "Attach a photo of the bill"}
          </Label>
        )}
        <input
          id="receipt-image"
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (!file) return;
            update({ image: URL.createObjectURL(file) });
            setOcrLoading(true);
            void parseReceipt(groupId, file)
              .then((receipt) => {
                const items = receipt.items.map((item, index) => ({
                  id: `ocr-${index}`,
                  name: item.name,
                  quantity: item.quantity,
                  price: item.unitPricePiastres / 100,
                  assignedTo: [],
                }));
                const next = { ...draft, items };
                if (receipt.suggestedTotalPiastres > 0) next.totalAmount = receipt.suggestedTotalPiastres / 100;
                setDraft(next);
              })
              .catch(() => {
                // The manual item editor remains available when OCR is unavailable.
              })
              .finally(() => setOcrLoading(false));
          }}
        />
      </div>

      {error && !splitError && <FormError message={error} />}

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
        <p className="text-caption text-muted-foreground">
          {pluralize(draft.participants.length, "person", "people")} ·{" "}
          {draft.splitType === "equal" ? "equal split" : "custom split"}
          {draft.totalAmount > 0 && (
            <>
              {" · "}
              <Money value={draft.totalAmount} size="xs" />
            </>
          )}
        </p>
        <Button onClick={onContinue} className="w-full sm:w-auto">
          Review expense
        </Button>
      </div>
    </div>
  );
}
