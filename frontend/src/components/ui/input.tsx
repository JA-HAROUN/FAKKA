import * as React from "react";

import { cn } from "@/lib/utils";

const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          // 16px text on mobile stops iOS Safari zooming on focus; 14px from sm up.
          "flex h-10 w-full rounded-md border border-input bg-card px-3 py-2 text-base transition-colors placeholder:text-muted-foreground file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground hover:border-border-strong disabled:cursor-not-allowed disabled:bg-surface disabled:opacity-60 aria-[invalid=true]:border-negative sm:text-sm",
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
