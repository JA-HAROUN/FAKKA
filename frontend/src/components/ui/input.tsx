import * as React from "react";

import { cn } from "@/lib/utils";

const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          // 16px text on mobile stops iOS Safari zooming on focus; 14px from sm up.
          "flex h-11 w-full rounded-lg border-2 border-input bg-card px-3.5 py-2 text-base transition-colors placeholder:text-muted-foreground file:border-0 file:bg-transparent file:text-body file:font-medium file:text-foreground hover:border-border-strong focus-visible:border-primary disabled:cursor-not-allowed disabled:bg-surface disabled:opacity-60 aria-[invalid=true]:border-negative sm:text-body",
          className,
        )}
        ref={ref}
        {...props}
      />
    );
  },
);
Input.displayName = "Input";

export { Input };
