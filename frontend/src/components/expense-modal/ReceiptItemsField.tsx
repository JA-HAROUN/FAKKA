import { useState } from "react";
import { ScanLine, Loader2, Plus, Trash2, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { PurchasedItem } from "@/types";
import { mockReceiptItems } from "@/utils/mockData";
import { formatAmount, itemsTotal } from "@/utils/calculations";
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
    }, 1200);
  }

  function updateItem(id: string, patch: Partial<PurchasedItem>) {
    onChange(items.map((i) => (i.id === id ? { ...i, ...patch } : i)));
  }

  return (
    <div className="rounded-xl border border-border">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-3 px-3 py-3 text-left"
      >
        <ScanLine className="size-4 text-muted-foreground" />
        <span className="flex-1 text-sm font-medium">
          Item list <span className="text-muted-foreground">(optional)</span>
        </span>
        {items.length > 0 && (
          <span className="text-xs tabular-nums text-muted-foreground">
            {items.length} items · {formatAmount(total)}
          </span>
        )}
        <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} />
      </button>

      {open && (
        <div className="space-y-3 border-t border-border p-3">
          <p className="text-xs text-muted-foreground">
            Scan a receipt or type items in by hand — it just records what was bought.
          </p>

          <div className="flex flex-wrap gap-2">
            <Label
              htmlFor="ocr-file"
              className="inline-flex cursor-pointer items-center gap-2 rounded-full bg-secondary px-4 py-2 text-sm font-medium hover:bg-secondary/80"
            >
              <ScanLine className="size-4" /> Scan a receipt
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
                  <Loader2 className="size-4 animate-spin" /> Reading…
                </>
              ) : (
                "Use a sample receipt"
              )}
            </Button>
          </div>

          {items.length > 0 && (
            <ul className="space-y-2">
              {items.map((item) => (
                <li key={item.id} className="flex flex-wrap items-center gap-2">
                  <Input
                    className="h-9 min-w-36 flex-1"
                    value={item.name}
                    onChange={(e) => updateItem(item.id, { name: e.target.value })}
                    placeholder="Item name"
                  />
                  <Input
                    className="h-9 w-16"
                    type="number"
                    min={1}
                    value={item.quantity}
                    onChange={(e) => updateItem(item.id, { quantity: Number(e.target.value) || 1 })}
                  />
                  <Input
                    className="h-9 w-24"
                    type="number"
                    min={0}
                    step="0.01"
                    value={item.price}
                    onChange={(e) => updateItem(item.id, { price: Number(e.target.value) || 0 })}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label="Remove item"
                    onClick={() => onChange(items.filter((i) => i.id !== item.id))}
                  >
                    <Trash2 className="size-4" />
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
              onChange([
                ...items,
                { id: newId(), name: "", quantity: 1, price: 0, assignedTo: [] },
              ])
            }
          >
            <Plus className="size-4" /> Add item
          </Button>
        </div>
      )}
    </div>
  );
}
