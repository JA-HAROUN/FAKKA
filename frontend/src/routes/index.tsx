import { useEffect, useState } from "react";
import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { ArrowRight, ReceiptText, Scale, Sparkles } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { LoginForm } from "@/components/auth/LoginForm";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { ThemeToggle } from "@/components/common/ThemeToggle";
import { useApp } from "@/context/AppContext";
import { cn } from "@/lib/utils";

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

/** A real slice of product output — the design system's money hierarchy on show. */
const preview = [
  { from: "Ahmed", to: "You", amount: "+200", owed: true },
  { from: "You", to: "Nour", amount: "−150", owed: false },
];

function BrandMark({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        // Squircle, not a circle: 16px on a 40px tile reads as a brand mark,
        // where a full round would read as a user avatar.
        "grid size-10 place-items-center rounded-lg bg-primary text-base font-extrabold text-primary-foreground shadow-sm",
        className,
      )}
      aria-hidden
    >
      F
    </span>
  );
}

function AuthPage() {
  const { currentUser, hydrated } = useApp();
  const navigate = useNavigate();
  const [tab, setTab] = useState("login");

  useEffect(() => {
    if (hydrated && currentUser) void navigate({ to: "/dashboard" });
  }, [hydrated, currentUser, navigate]);

  return (
    <div className="min-h-dvh bg-background lg:grid lg:grid-cols-[minmax(0,1fr)_27rem] xl:grid-cols-[minmax(0,1fr)_31rem]">
      {/* Brand column — desktop only. Explains the product in the product's own
          visual language rather than as a marketing hero. */}
      <aside className="relative hidden flex-col justify-between overflow-hidden border-r border-border bg-surface p-10 lg:flex xl:p-14">
        <div className="mx-auto flex w-full max-w-md items-center gap-2.5">
          <BrandMark />
          <span className="text-lead font-bold tracking-tight">Fakka</span>
        </div>

        <div className="mx-auto w-full max-w-md space-y-9">
          <div className="space-y-3">
            <h1 className="text-display font-extrabold tracking-tight text-balance">
              Shared expenses, settled without the awkward maths.
            </h1>
            <p className="text-body text-muted-foreground">
              Fakka keeps track of what everyone paid and works out the shortest way to make the
              group even again.
            </p>
          </div>

          <ul className="space-y-4">
            {points.map((point) => (
              <li key={point.title} className="flex gap-3.5">
                <span
                  className="grid size-9 shrink-0 place-items-center rounded-md border border-border bg-card text-primary shadow-xs"
                  aria-hidden
                >
                  <point.icon className="size-4" />
                </span>
                <div className="space-y-0.5 pt-0.5">
                  <p className="text-label font-semibold text-foreground">{point.title}</p>
                  <p className="text-label text-muted-foreground">{point.body}</p>
                </div>
              </li>
            ))}
          </ul>

          {/* The money hierarchy in miniature: eyebrow label, hero figure, then
              a settlement list with green/red tabular amounts and status dots. */}
          <div className="panel max-w-sm space-y-4 p-5">
            <div>
              <p className="eyebrow">Your position</p>
              <p className="amount-lg mt-1 text-positive">+50 EGP</p>
              <p className="text-caption text-muted-foreground">across 3 groups · net you are owed</p>
            </div>
            <div className="h-px bg-border" />
            <ul className="space-y-3">
              {preview.map((row) => (
                <li key={row.from + row.to} className="flex items-center gap-2 text-label">
                  <span
                    className={cn(
                      "size-1.5 shrink-0 rounded-full",
                      row.owed ? "bg-positive-strong" : "bg-negative-strong",
                    )}
                    aria-hidden
                  />
                  <span className="truncate font-semibold text-foreground">{row.from}</span>
                  <ArrowRight className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
                  <span className="truncate font-semibold text-foreground">{row.to}</span>
                  <span
                    className={cn(
                      "ml-auto font-bold tabular-nums",
                      row.owed ? "text-positive" : "text-negative",
                    )}
                  >
                    {row.amount} EGP
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <p className="mx-auto w-full max-w-md text-caption text-muted-foreground">
          Demo data is included, so you can explore straight away.
        </p>
      </aside>

      <div className="relative flex min-h-dvh items-center justify-center px-5 py-10 sm:px-8 lg:min-h-0">
        {/* Theme toggle lives top-right on every auth viewport so dark mode is
            reachable before sign-in, not only from inside the app shell. */}
        <div className="absolute right-4 top-4 sm:right-6 sm:top-6">
          <ThemeToggle />
        </div>

        <div className="w-full max-w-sm space-y-8">
          <div className="space-y-3 lg:hidden">
            <BrandMark />
            <div className="space-y-1 pt-1">
              <h1 className="text-title font-extrabold tracking-tight">Fakka</h1>
              <p className="text-body text-muted-foreground">
                Shared expenses, settled without the awkward maths.
              </p>
            </div>
          </div>

          <div className="hidden space-y-1 lg:block">
            <h2 className="text-title font-extrabold tracking-tight">
              {tab === "login" ? "Welcome back" : "Create your account"}
            </h2>
            <p className="text-body text-muted-foreground">
              {tab === "login"
                ? "Sign in to see where your money stands."
                : "Start splitting expenses with your friends."}
            </p>
          </div>

          <Tabs value={tab} onValueChange={setTab}>
            <TabsList className="w-full">
              <TabsTrigger value="login">Sign in</TabsTrigger>
              <TabsTrigger value="register">Create account</TabsTrigger>
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
