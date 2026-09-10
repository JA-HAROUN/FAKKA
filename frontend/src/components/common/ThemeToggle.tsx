import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTheme } from "@/context/ThemeContext";
import { cn } from "@/lib/utils";

/**
 * Light/dark switch. The two icons swap via CSS rather than React state: the
 * boot script stamps `.dark` on <html> before first paint, so the correct icon
 * is right on the first frame and there's no hydration mismatch to smooth over.
 * That's also why the label is state-independent.
 */
export function ThemeToggle({ className }: { className?: string }) {
  const { toggleTheme } = useTheme();

  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      onClick={toggleTheme}
      aria-label="Toggle dark mode"
      title="Toggle dark mode"
      className={cn("shrink-0 text-muted-foreground hover:text-foreground", className)}
    >
      <Moon className="dark:hidden" aria-hidden />
      <Sun className="hidden dark:block" aria-hidden />
    </Button>
  );
}
