import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-sm border px-1.5 py-0.5 text-[11px] font-medium leading-4 [&_svg]:size-3 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: "border-transparent bg-primary text-primary-foreground",
        secondary: "border-transparent bg-secondary text-secondary-foreground",
        outline: "border-border text-muted-foreground",
        destructive: "border-transparent bg-destructive text-destructive-foreground",
        // Financial / status meaning — always paired with a text label.
        positive: "border-transparent bg-positive-soft text-positive",
        negative: "border-transparent bg-negative-soft text-negative",
        neutral: "border-transparent bg-neutral-soft text-muted-foreground",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}

export { Badge, badgeVariants };
