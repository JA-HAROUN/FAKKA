import { useEffect, useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LoginForm } from "@/components/auth/LoginForm";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { useApp } from "@/context/AppContext";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Fakka — Split group expenses without the awkward math" },
      {
        name: "description",
        content:
          "Track shared expenses with friends, see who owes whom at a glance, and settle up in the fewest possible payments.",
      },
      { property: "og:title", content: "Fakka — Split group expenses the easy way" },
      {
        property: "og:description",
        content: "Create groups, add expenses, and settle balances with friends in seconds.",
      },
    ],
  }),
  component: AuthPage,
});

function AuthPage() {
  const { currentUser, hydrated } = useApp();
  const navigate = useNavigate();
  const [tab, setTab] = useState("login");

  useEffect(() => {
    if (hydrated && currentUser) void navigate({ to: "/dashboard" });
  }, [hydrated, currentUser, navigate]);

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-primary-soft p-10 lg:flex">
        <div className="flex items-center gap-2">
          <span className="grid size-10 place-items-center rounded-xl bg-primary text-lg text-primary-foreground">
            ⇄
          </span>
          <span className="text-xl font-bold tracking-tight">Fakka</span>
        </div>
        <div className="space-y-4">
          <h1 className="text-4xl font-bold leading-tight tracking-tight">
            Shared expenses, zero awkward math.
          </h1>
          <p className="max-w-md text-muted-foreground">
            Add what you spent, and Fakka works out who owes whom — simplified into the fewest
            possible payments.
          </p>
          <ul className="space-y-2 text-sm font-medium">
            <li>🧾 Optionally scan a receipt to list what was bought</li>
            <li>✨ Or just describe the expense in plain language</li>
            <li>📤 Export a full group report anytime</li>
          </ul>
        </div>
        <p className="text-xs text-muted-foreground">Demo data included — sign in to explore.</p>
      </div>

      <div className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm space-y-6">
          <div className="space-y-1 lg:hidden">
            <span className="grid size-10 place-items-center rounded-xl bg-primary text-lg text-primary-foreground">
              ⇄
            </span>
            <h1 className="pt-2 text-2xl font-bold tracking-tight">Fakka</h1>
            <p className="text-sm text-muted-foreground">
              Split group expenses without the awkward math.
            </p>
          </div>

          <Tabs value={tab} onValueChange={setTab}>
            <TabsList className="w-full">
              <TabsTrigger value="login" className="flex-1">
                Sign in
              </TabsTrigger>
              <TabsTrigger value="register" className="flex-1">
                Create account
              </TabsTrigger>
            </TabsList>
            <TabsContent value="login" className="mt-6">
              <LoginForm onSuccess={() => void navigate({ to: "/dashboard" })} />
            </TabsContent>
            <TabsContent value="register" className="mt-6">
              <RegisterForm onSuccess={() => void navigate({ to: "/dashboard" })} />
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </div>
  );
}
