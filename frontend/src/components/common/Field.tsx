import { useId, type ReactNode } from "react";
import { AlertCircle } from "lucide-react";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

/**
 * Label → control → hint/error, wired together for screen readers.
 * `children` receives the generated id so the label always points at the
 * control and errors are announced via aria-describedby.
 */
export function Field({
  label,
  hint,
  error,
  required,
  children,
  className,
}: {
  label: string;
  hint?: string | undefined;
  error?: string | null | undefined;
  required?: boolean | undefined;
  children: (props: {
    id: string;
    "aria-describedby"?: string;
    "aria-invalid"?: boolean;
  }) => ReactNode;
  className?: string | undefined;
}) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  return (
    <div className={cn("space-y-1.5", className)}>
      <Label htmlFor={id} className="text-label font-semibold">
        {label}
        {required && (
          <span className="ml-0.5 text-negative" aria-hidden>
            *
          </span>
        )}
      </Label>
      {children({
        id,
        ...(describedBy ? { "aria-describedby": describedBy } : {}),
        ...(error ? { "aria-invalid": true } : {}),
      })}
      {error ? (
        <p
          id={`${id}-error`}
          className="flex items-center gap-1.5 text-caption font-medium text-negative"
        >
          <AlertCircle className="size-3.5 shrink-0" aria-hidden />
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-caption text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

/** Form-level error, shown above the submit action. */
export function FormError({ message }: { message?: string | null | undefined }) {
  if (!message) return null;
  return (
    <p
      role="alert"
      className="flex items-start gap-2 rounded-md border border-negative/25 bg-negative-soft px-3 py-2.5 text-label font-medium text-negative"
    >
      <AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden />
      {message}
    </p>
  );
}
