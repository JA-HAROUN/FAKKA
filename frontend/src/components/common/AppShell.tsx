import { Link, useNavigate } from "@tanstack/react-router";
import { LayoutGrid, Users, LogOut } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { UserAvatar } from "@/components/common/UserAvatar";
import { useApp } from "@/context/AppContext";

const navItems = [
  { to: "/dashboard", label: "Groups", icon: LayoutGrid },
  { to: "/friends", label: "Friends", icon: Users },
] as const;

export function AppShell({ children }: { children: ReactNode }) {
  const { currentUser, signOut } = useApp();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-background pb-24 md:pb-0">
      <header className="sticky top-0 z-30 border-b border-border/70 bg-background/85 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between gap-3 px-4">
          <Link to="/dashboard" className="flex items-center gap-2">
            <span className="grid size-9 place-items-center rounded-xl bg-primary text-lg text-primary-foreground">
              ⇄
            </span>
            <span className="text-lg font-bold tracking-tight">Fakka</span>
          </Link>

          <nav className="hidden items-center gap-1 md:flex">
            {navItems.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className="rounded-full px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
                activeProps={{ className: "bg-primary-soft text-foreground" }}
              >
                {item.label}
              </Link>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            {currentUser && <UserAvatar user={currentUser} size="sm" />}
            <Button
              variant="ghost"
              size="icon"
              aria-label="Sign out"
              onClick={() => {
                signOut();
                navigate({ to: "/" });
              }}
            >
              <LogOut className="size-4" />
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl px-4 py-6">{children}</main>

      <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card/95 backdrop-blur md:hidden">
        <div className="flex">
          {navItems.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className="flex flex-1 flex-col items-center gap-1 py-3 text-xs font-medium text-muted-foreground"
              activeProps={{ className: "text-primary" }}
            >
              <item.icon className="size-5" />
              {item.label}
            </Link>
          ))}
        </div>
      </nav>
    </div>
  );
}
