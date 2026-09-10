import { useState } from "react";
import { ChevronDown, Loader2, Plus, ScanLine, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { PurchasedItem } from "@/types";
import { mockReceiptItems } from "@/utils/mockData";
import { formatAmount, itemsTotal } from "@/utils/calculations";
import { pluralize } from "@/utils/format";
import { cn } from "@/lib/utils";

const newId = () => `i${Math.random().toString(36).slice(2, 9)}`;

/** Optional add-on: lists the items bought. It never changes how the expense is split. */
export function ReceiptItemsField({
  items,
  onChange,
}: {
  items: PurchasedItem[];
  onChange: (items: PurchasedItem[]) => void;
}) {
  const [open, setOpen] = useState(items.length > 0);
  const [loading, setLoading] = useState(false);

  const total = itemsTotal(items);

  function runScan() {
    setLoading(true);
    setTimeout(() => {
      onChange([
        ...items,
        ...mockReceiptItems.map((i) => ({
          id: newId(),
          name: i.name,
          quantity: i.quantity,
          price: i.price,
          assignedTo: [],
        })),
      ]);
      setLoading(false);
      setOpen(true);
    }, 1200);
  }

  function updateItem(id: string, patch: Partial<PurchasedItem>) {
    onChange(items.map((i) => (i.id === id ? { ...i, ...patch } : i)));
  }

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="row-hover flex w-full cursor-pointer items-center gap-3 px-3 py-3 text-left"
      >
        <ScanLine className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <span className="min-w-0 flex-1 text-[13px] font-medium">
          Item list <span className="font-normal text-muted-foreground">· optional</span>
        </span>
        {items.length > 0 && (
          <span className="text-xs tabular-nums text-muted-foreground">
            {pluralize(items.length, "item")} · {formatAmount(total)}
          </span>
        )}
        <ChevronDown
          className={cn(
            "size-4 shrink-0 text-muted-foreground transition-transform",
            open && "rotate-180",
          )}
          aria-hidden
        />
      </button>

      {open && (
        <div className="space-y-3 border-t border-border p-3">
          <p className="text-xs text-muted-foreground">
            Scan a receipt or type the items in by hand — this just records what was bought.
          </p>

          <div className="flex flex-wrap gap-2">
            <Label
              htmlFor="ocr-file"
              className="inline-flex h-8 cursor-pointer items-center gap-2 rounded-md border border-input bg-card px-3 text-[13px] font-medium transition-colors hover:bg-surface"
            >
              <ScanLine className="size-4" aria-hidden /> Scan a receipt
            </Label>
            <input
              id="ocr-file"
              type="file"
              accept="image/*"
              capture="environment"
              className="hidden"
              onChange={(e) => {
                if (e.target.files?.[0]) runScan();
              }}
            />
            <Button type="button" variant="ghost" size="sm" disabled={loading} onClick={runScan}>
              {loading ? (
                <>
                  <Loader2 className="animate-spin" aria-hidden /> Reading receipt…
                </>
              ) : (
                "Use a sample receipt"
              )}
            </Button>
          </div>

          {items.length > 0 && (
            <ul className="space-y-2">
              <li className="hidden gap-2 px-1 text-xs font-medium text-muted-foreground sm:flex">
                <span className="flex-1">Item</span>
                <span className="w-14 text-center">Qty</span>
                <span className="w-24 text-right">Price</span>
                <span className="w-8" />
              </li>
              {items.map((item) => (
                <li key={item.id} className="flex items-center gap-2">
                  <Input
                    className="h-9 min-w-0 flex-1"
                    value={item.name}
                    onChange={(e) => updateItem(item.id, { name: e.target.value })}
                    placeholder="Item name"
                    aria-label="Item name"
                  />
                  <Input
                    className="h-9 w-14 text-center tabular-nums"
                    type="number"
                    inputMode="numeric"
                    min={1}
                    value={item.quantity}
                    onChange={(e) => updateItem(item.id, { quantity: Number(e.target.value) || 1 })}
                    aria-label="Quantity"
                  />
                  <Input
                    className="h-9 w-24 text-right tabular-nums"
                    type="number"
                    inputMode="decimal"
                    min={0}
                    step="0.01"
                    value={item.price}
                    onChange={(e) => updateItem(item.id, { price: Number(e.target.value) || 0 })}
                    aria-label="Price"
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Remove ${item.name || "item"}`}
                    onClick={() => onChange(items.filter((i) => i.id !== item.id))}
                  >
                    <Trash2 aria-hidden />
                  </Button>
                </li>
              ))}
            </ul>
          )}

          <Button
            type="button"
            variant="outline"
            size="sm"
            className="w-full"
            onClick={() =>
              onChange([...items, { id: newId(), name: "", quantity: 1, price: 0, assignedTo: [] }])
            }
          >
            <Plus aria-hidden /> Add item
          </Button>
        </div>
      )}
    </div>
  );
}
