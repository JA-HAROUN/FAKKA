import { useState } from "react";
import { Sparkles, Loader2, Info } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import type { ExpenseDraft, User } from "@/types";
import { parseExpenseText } from "@/utils/parseNaturalLanguage";

const EXAMPLE =
  "John paid 900 EGP for dinner. John and Ahmed shared the pizza and Mohamed had the burger.";

export function AiExpenseInput({
  members,
  fallbackPayer,
  onParsed,
  onSwitchToManual,
}: {
  members: User[];
  fallbackPayer: string;
  onParsed: (draft: ExpenseDraft) => void;
  onSwitchToManual: () => void;
}) {
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(false);

  return (
    <div className="space-y-4">
      <div className="rounded-xl bg-primary-soft p-4">
        <p className="flex items-center gap-2 text-sm font-semibold">
          <Sparkles className="size-4" /> Describe the expense in plain language
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          Fakka fills in the same form you'd complete manually — you review and edit everything
          before it's saved.
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="ai-text">Your description</Label>
        <Textarea
          id="ai-text"
          rows={5}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={`e.g. ${EXAMPLE}`}
        />
        <button
          type="button"
          className="text-xs font-medium text-primary underline-offset-2 hover:underline"
          onClick={() => setText(EXAMPLE)}
        >
          Use the example
        </button>
      </div>

      <Button
        className="w-full"
        size="lg"
        disabled={!text.trim() || loading}
        onClick={() => {
          setLoading(true);
          setTimeout(() => {
            onParsed(parseExpenseText(text, members, fallbackPayer));
            setLoading(false);
          }, 1200);
        }}
      >
        {loading ? (
          <>
            <Loader2 className="size-4 animate-spin" /> Reading your description…
          </>
        ) : (
          <>
            <Sparkles className="size-4" /> Generate expense
          </>
        )}
      </Button>

      <p className="flex items-start gap-2 rounded-xl bg-secondary p-3 text-xs text-muted-foreground">
        <Info className="mt-0.5 size-3.5 shrink-0" />
        <span>
          AI unavailable?{" "}
          <button
            type="button"
            onClick={onSwitchToManual}
            className="font-semibold text-foreground underline underline-offset-2"
          >
            Switch to Manual Entry
          </button>{" "}
          — every field can be filled by hand.
        </span>
      </p>
    </div>
  );
}
