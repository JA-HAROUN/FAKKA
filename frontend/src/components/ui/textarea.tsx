import * as React from "react";

import { cn } from "@/lib/utils";

const Textarea = React.forwardRef<HTMLTextAreaElement, React.ComponentProps<"textarea">>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        className={cn(
          "flex min-h-20 w-full rounded-lg border-2 border-input bg-card px-3.5 py-2.5 text-base transition-colors placeholder:text-muted-foreground hover:border-border-strong focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-negative sm:text-body",
          className,
        )}
        ref={ref}
        {...props}
      />
    );
  },
);
Textarea.displayName = "Textarea";

export { Textarea };
