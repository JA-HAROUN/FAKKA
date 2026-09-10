import { useState } from "react";
import { Info, Loader2, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Field } from "@/components/common/Field";
import type { ExpenseDraft, User } from "@/types";
import { parseExpenseText } from "@/utils/parseNaturalLanguage";

const EXAMPLE =
  "John paid 900 EGP for dinner. John and Ahmed shared the pizza and Mohamed had the burger.";

/**
 * Plain-language entry. It only pre-fills the manual form — nothing is saved
 * until the user reviews it — and the manual route stays one click away if it
 * misreads the description.
 */
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
      <Field label="Describe the expense" hint="Include the amount, who paid, and who shared it.">
        {(field) => (
          <Textarea
            {...field}
            rows={5}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={EXAMPLE}
          />
        )}
      </Field>

      <div className="flex flex-wrap items-center gap-3">
        <Button
          className="w-full sm:w-auto"
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
              <Loader2 className="animate-spin" aria-hidden /> Reading your description…
            </>
          ) : (
            <>
              <Sparkles aria-hidden /> Fill in the form
            </>
          )}
        </Button>
        <button
          type="button"
          className="cursor-pointer text-caption font-semibold text-primary hover:underline"
          onClick={() => setText(EXAMPLE)}
        >
          Use the example
        </button>
      </div>

      <p className="panel-inset flex items-start gap-2 px-3 py-2.5 text-caption text-muted-foreground">
        <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
        <span>
          You review and edit everything before it's saved. If this can't read your description,{" "}
          <button
            type="button"
            onClick={onSwitchToManual}
            className="cursor-pointer font-semibold text-foreground underline underline-offset-2"
          >
            enter it manually
          </button>
          .
        </span>
      </p>
    </div>
  );
}
