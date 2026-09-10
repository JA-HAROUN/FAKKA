import { useEffect, type ReactNode } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useApp } from "@/context/AppContext";
import { AppShell } from "@/components/common/AppShell";
import { PageSkeleton } from "@/components/common/Skeletons";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { currentUser, hydrated } = useApp();
  const navigate = useNavigate();

  useEffect(() => {
    if (hydrated && !currentUser) void navigate({ to: "/" });
  }, [hydrated, currentUser, navigate]);

  // Persisted state is restored on the client, so the first paint has no data
  // to show. A skeleton in the real layout beats a blank screen or a spinner.
  if (!hydrated || !currentUser) {
    return (
      <AppShell>
        <PageSkeleton />
      </AppShell>
    );
  }

  return <AppShell>{children}</AppShell>;
}
