import { useState, type ComponentProps } from "react";
import { Eye, EyeOff } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/** Password field with a show/hide toggle that stays keyboard reachable. */
export function PasswordInput({ className, ...props }: ComponentProps<"input">) {
  const [visible, setVisible] = useState(false);
  const Icon = visible ? EyeOff : Eye;

  return (
    <div className="relative">
      <Input {...props} type={visible ? "text" : "password"} className={cn("pr-10", className)} />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? "Hide password" : "Show password"}
        className="absolute inset-y-0 right-0 grid w-10 cursor-pointer place-items-center rounded-r-md text-muted-foreground transition-colors hover:text-foreground"
      >
        <Icon className="size-4" aria-hidden />
      </button>
    </div>
  );
}
