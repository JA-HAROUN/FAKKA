import { useEffect, useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ArrowRight, ReceiptText, Scale, Sparkles } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LoginForm } from "@/components/auth/LoginForm";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { useApp } from "@/context/AppContext";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Sign in — Fakka" },
      {
        name: "description",
        content:
          "Track shared expenses with friends, see who owes whom at a glance, and settle up in the fewest possible payments.",
      },
      { property: "og:title", content: "Fakka — shared expenses, settled simply" },
      {
        property: "og:description",
        content: "Create groups, add expenses, and settle balances with friends in seconds.",
      },
    ],
  }),
  component: AuthPage,
});

const points = [
  {
    icon: Scale,
    title: "Balances that explain themselves",
    body: "Every expense updates who owes whom — no spreadsheets, no mental arithmetic.",
  },
  {
    icon: Sparkles,
    title: "Add an expense however you like",
    body: "Fill the form, or describe it in plain language and review what Fakka fills in.",
  },
  {
    icon: ReceiptText,
    title: "Settle in the fewest payments",
    body: "Debts are simplified across the group, then exported as a report when you need one.",
  },
];

const preview = [
  { from: "Ahmed", to: "You", amount: "200 EGP", owed: true },
  { from: "You", to: "Nour", amount: "150 EGP", owed: false },
];

function AuthPage() {
  const { currentUser, hydrated } = useApp();
  const navigate = useNavigate();
  const [tab, setTab] = useState("login");

  useEffect(() => {
    if (hydrated && currentUser) void navigate({ to: "/dashboard" });
  }, [hydrated, currentUser, navigate]);

  return (
    <div className="min-h-dvh bg-background lg:grid lg:grid-cols-[minmax(0,1fr)_26rem] xl:grid-cols-[minmax(0,1fr)_30rem]">
      {/* Brand column — desktop only. Explains the product, in the product's own
          visual language rather than as a marketing hero. */}
      <aside className="hidden flex-col justify-between border-r border-border bg-surface p-10 lg:flex xl:p-14">
        <div className="mx-auto flex w-full max-w-md items-center gap-2.5">
          <span
            className="grid size-8 place-items-center rounded-lg bg-primary text-sm font-bold text-primary-foreground"
            aria-hidden
          >
            F
          </span>
          <span className="text-[15px] font-semibold tracking-tight">Fakka</span>
        </div>

        <div className="mx-auto w-full max-w-md space-y-8">
          <div className="space-y-3">
            <h1 className="text-2xl font-semibold tracking-tight text-balance">
              Shared expenses, settled without the awkward maths.
            </h1>
            <p className="text-sm text-muted-foreground">
              Fakka keeps track of what everyone paid and works out the shortest way to make the
              group even again.
            </p>
          </div>

          <ul className="space-y-5">
            {points.map((point) => (
              <li key={point.title} className="flex gap-3">
                <span
                  className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg border border-border bg-card text-muted-foreground"
                  aria-hidden
                >
                  <point.icon className="size-4" />
                </span>
                <div className="space-y-0.5">
                  <p className="text-[13px] font-semibold">{point.title}</p>
                  <p className="text-[13px] text-muted-foreground">{point.body}</p>
                </div>
              </li>
            ))}
          </ul>

          {/* A real slice of the product's output, not an illustration. */}
          <div className="panel max-w-xs p-4">
            <p className="text-xs font-medium text-muted-foreground">Who owes whom</p>
            <ul className="mt-3 space-y-2.5 text-[13px]">
              {preview.map((row) => (
                <li key={row.from + row.to} className="flex items-center gap-2">
                  <span className="truncate font-medium">{row.from}</span>
                  <ArrowRight className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
                  <span className="truncate font-medium">{row.to}</span>
                  <span
                    className={
                      row.owed
                        ? "ml-auto font-semibold tabular-nums text-positive"
                        : "ml-auto font-semibold tabular-nums text-negative"
                    }
                  >
                    {row.amount}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <p className="mx-auto w-full max-w-md text-xs text-muted-foreground">
          Demo data is included, so you can explore straight away.
        </p>
      </aside>

      <div className="flex min-h-dvh items-center justify-center px-5 py-10 sm:px-8 lg:min-h-0">
        <div className="w-full max-w-sm space-y-7">
          <div className="space-y-2 lg:hidden">
            <span
              className="grid size-8 place-items-center rounded-lg bg-primary text-sm font-bold text-primary-foreground"
              aria-hidden
            >
              F
            </span>
            <h1 className="pt-1 text-xl font-semibold tracking-tight">Fakka</h1>
            <p className="text-sm text-muted-foreground">
              Shared expenses, settled without the awkward maths.
            </p>
          </div>

          <div className="hidden space-y-1 lg:block">
            <h2 className="text-xl font-semibold tracking-tight">
              {tab === "login" ? "Welcome back" : "Create your account"}
            </h2>
            <p className="text-sm text-muted-foreground">
              {tab === "login"
                ? "Sign in to see where your money stands."
                : "Start splitting expenses with your friends."}
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
