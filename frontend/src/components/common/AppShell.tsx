import { Link, useNavigate } from "@tanstack/react-router";
import { LayoutGrid, LogOut, Users } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { UserAvatar } from "@/components/common/UserAvatar";
import { ThemeToggle } from "@/components/common/ThemeToggle";
import { useApp } from "@/context/AppContext";
import { cn } from "@/lib/utils";

const navItems = [
  { to: "/dashboard", label: "Groups", icon: LayoutGrid },
  { to: "/friends", label: "Friends", icon: Users },
] as const;

/**
 * Navigation: a persistent sidebar from `lg` up, a top bar plus bottom tab bar
 * below it. The mobile bar keeps both destinations inside thumb reach and is
 * padded for the home-indicator inset.
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-background lg:flex">
      <DesktopSidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <MobileTopBar />
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-5 pb-28 sm:px-6 lg:px-8 lg:py-8 lg:pb-12">
          {children}
        </main>
      </div>
      <MobileTabBar />
    </div>
  );
}

function BrandMark({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        // rounded-md, not -lg: on a 32px tile the 16px radius would round it
        // into a circle and the brand mark would read as an avatar.
        "grid size-8 shrink-0 place-items-center rounded-md bg-primary text-sm font-bold text-primary-foreground",
        className,
      )}
      aria-hidden
    >
      F
    </span>
  );
}

function DesktopSidebar() {
  return (
    <aside className="sticky top-0 hidden h-dvh w-60 shrink-0 flex-col border-r border-border bg-sidebar lg:flex">
      <div className="flex h-16 items-center gap-2.5 px-5">
        <BrandMark />
        <span className="text-[15px] font-semibold tracking-tight">Fakka</span>
      </div>

      <nav aria-label="Main" className="flex-1 space-y-0.5 px-3 py-2">
        {navItems.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            className="flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-surface hover:text-foreground"
            activeProps={{
              className: "bg-surface text-foreground",
              "aria-current": "page",
            }}
          >
            {({ isActive }) => (
              <>
                <item.icon
                  className={cn("size-4 shrink-0", isActive ? "text-primary" : "text-current")}
                  aria-hidden
                />
                {item.label}
              </>
            )}
          </Link>
        ))}
      </nav>

      <div className="flex items-center gap-1 border-t border-border p-3">
        <AccountMenu align="start" side="top" fullWidth />
        <ThemeToggle />
      </div>
    </aside>
  );
}

function MobileTopBar() {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-background/90 backdrop-blur lg:hidden">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <Link to="/dashboard" className="flex items-center gap-2">
          <BrandMark />
          <span className="text-[15px] font-semibold tracking-tight">Fakka</span>
        </Link>
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <AccountMenu align="end" side="bottom" />
        </div>
      </div>
    </header>
  );
}

function MobileTabBar() {
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden"
    >
      <div className="mx-auto flex max-w-md">
        {navItems.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            className="relative flex min-h-14 flex-1 flex-col items-center justify-center gap-1 py-2 text-[11px] font-medium text-muted-foreground transition-colors"
            activeProps={{ className: "text-primary", "aria-current": "page" }}
          >
            {({ isActive }) => (
              <>
                {isActive && (
                  <span
                    className="absolute inset-x-6 top-0 h-0.5 rounded-full bg-primary"
                    aria-hidden
                  />
                )}
                <item.icon className="size-5" aria-hidden />
                {item.label}
              </>
            )}
          </Link>
        ))}
      </div>
    </nav>
  );
}

function AccountMenu({
  align,
  side,
  fullWidth = false,
}: {
  align: "start" | "end";
  side: "top" | "bottom";
  fullWidth?: boolean;
}) {
  const { currentUser, signOut } = useApp();
  const navigate = useNavigate();
  if (!currentUser) return null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className={cn(
            "h-auto gap-2.5 px-2 py-2",
            fullWidth ? "w-full min-w-0 flex-1 justify-start" : "justify-center",
          )}
          aria-label="Account menu"
        >
          <UserAvatar user={currentUser} size="sm" />
          {fullWidth && (
            <span className="min-w-0 flex-1 text-left">
              <span className="block truncate text-[13px] font-medium text-foreground">
                {currentUser.name}
              </span>
              <span className="block truncate text-xs font-normal text-muted-foreground">
                {currentUser.email}
              </span>
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align={align} side={side} className="w-60">
        <DropdownMenuLabel className="font-normal">
          <span className="block truncate text-[13px] font-medium">{currentUser.name}</span>
          <span className="block truncate text-xs text-muted-foreground">{currentUser.email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          onSelect={() => {
            signOut();
            void navigate({ to: "/" });
          }}
        >
          <LogOut className="size-4" aria-hidden />
          Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
