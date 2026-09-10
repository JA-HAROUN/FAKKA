import { useEffect, type ReactNode } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useApp } from "@/context/AppContext";
import { AppShell } from "@/components/common/AppShell";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { currentUser, hydrated } = useApp();
  const navigate = useNavigate();

  useEffect(() => {
    if (hydrated && !currentUser) void navigate({ to: "/" });
  }, [hydrated, currentUser, navigate]);

  if (!hydrated || !currentUser) {
    return (
      <div className="grid min-h-screen place-items-center">
        <p className="text-sm text-muted-foreground">Loading Fakka…</p>
      </div>
    );
  }

  return <AppShell>{children}</AppShell>;
}
