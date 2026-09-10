import type { CSSProperties, ReactNode } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ThemeToggle } from "@/components/common/ThemeToggle";
import { cn } from "@/lib/utils";

/**
 * Developer reference for the design system — not a product screen and not
 * linked from the app. Every token and primitive on one scroll, so a change to
 * styles.css can be eyeballed in both themes before it reaches a real page.
 */
export const Route = createFileRoute("/style-preview")({
  component: StylePreview,
});

/* ── Local scaffolding ─────────────────────────────────────────────────────
   Colour tokens have no utility class (they're consumed as `bg-primary`,
   not `bg-[var(--primary)]`), so the swatches read them through inline
   custom-property lookups. That's the documented exception to "tokens only". */

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <section className="space-y-5">
      <div>
        <h2 className="section-label">{title}</h2>
        {note ? <p className="text-caption text-muted-foreground">{note}</p> : null}
      </div>
      {children}
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-2.5">
      <span className="eyebrow">{label}</span>
      <div className="flex flex-wrap items-center gap-3">{children}</div>
    </div>
  );
}

function Swatch({ token, label }: { token: string; label?: string }) {
  return (
    <div className="w-24 space-y-1.5">
      <div
        className="h-14 rounded-lg border border-border shadow-xs"
        style={{ background: `var(${token})` }}
      />
      <p className="truncate text-caption font-medium text-foreground">{label ?? token}</p>
      {label ? <p className="truncate text-caption text-muted-foreground">{token}</p> : null}
    </div>
  );
}

const CATEGORIES = [
  { token: "--chart-1", name: "Food", share: 34 },
  { token: "--chart-2", name: "Transport", share: 21 },
  { token: "--chart-3", name: "Entertainment", share: 15 },
  { token: "--chart-4", name: "Shopping", share: 12 },
  { token: "--chart-5", name: "Stays", share: 9 },
  { token: "--chart-6", name: "Utilities", share: 6 },
  { token: "--chart-7", name: "Other", share: 3 },
];

const TYPE_SCALE = [
  { cls: "text-caption", token: "text-caption", px: "12" },
  { cls: "text-label", token: "text-label", px: "13" },
  { cls: "text-body", token: "text-body", px: "14 · default" },
  { cls: "text-lead", token: "text-lead", px: "15" },
  { cls: "text-subtitle", token: "text-subtitle", px: "17" },
  { cls: "text-title", token: "text-title", px: "22" },
  { cls: "text-display", token: "text-display", px: "28" },
];

const RADII = ["rounded-xs", "rounded-sm", "rounded-md", "rounded-lg", "rounded-xl", "rounded-2xl"];
const SHADOWS = ["shadow-xs", "shadow-sm", "shadow-md", "shadow-lg"];

const SETTLEMENTS = [
  { from: "Ahmed", to: "You", amount: "+200", owed: true },
  { from: "You", to: "Nour", amount: "−150", owed: false },
  { from: "Youssef", to: "You", amount: "+75", owed: true },
];

function StylePreview() {
  return (
    <div className="min-h-dvh bg-background">
      <div className="mx-auto max-w-4xl space-y-12 px-5 py-10 sm:px-8">
        <header className="flex items-start justify-between gap-4">
          <div className="space-y-1">
            <p className="eyebrow">Internal reference</p>
            <h1 className="text-display font-extrabold tracking-tight">Design system</h1>
            <p className="text-body text-muted-foreground">
              Every token and primitive in one place. Flip the theme to check both palettes.
            </p>
          </div>
          <ThemeToggle />
        </header>

        <Section title="Brand & surfaces" note="One teal owns every action; greys carry the canvas.">
          <div className="flex flex-wrap gap-4">
            <Swatch token="--primary" label="Brand" />
            <Swatch token="--primary-soft" label="Brand soft" />
            <Swatch token="--background" label="Canvas" />
            <Swatch token="--surface" label="Surface" />
            <Swatch token="--card" label="Card" />
            <Swatch token="--border" label="Border" />
          </div>
        </Section>

        <Section
          title="Money"
          note="Three weights each: readable text, pale pill background, vivid dot/bar fill."
        >
          <div className="flex flex-wrap gap-4">
            <Swatch token="--positive" label="Owed" />
            <Swatch token="--positive-soft" label="Owed soft" />
            <Swatch token="--positive-strong" label="Owed strong" />
            <Swatch token="--negative" label="Owe" />
            <Swatch token="--negative-soft" label="Owe soft" />
            <Swatch token="--negative-strong" label="Owe strong" />
            <Swatch token="--neutral" label="Settled" />
            <Swatch token="--neutral-soft" label="Settled soft" />
          </div>
        </Section>

        <Section
          title="Categories"
          note="Deliberately avoids the money green/red and the brand teal — a category can never read as a balance."
        >
          <div className="flex flex-wrap gap-x-5 gap-y-3">
            {CATEGORIES.map((c) => (
              <span key={c.token} className="flex items-center gap-2">
                <span className="category-dot" style={{ background: `var(${c.token})` }} />
                <span className="text-label font-medium text-foreground">{c.name}</span>
                <span className="text-caption text-muted-foreground">{c.token}</span>
              </span>
            ))}
          </div>
        </Section>

        <Section title="Type scale" note="Named sizes only — never text-[13px].">
          <div className="panel divide-y divide-border">
            {TYPE_SCALE.map((t) => (
              <div key={t.token} className="flex items-baseline gap-4 px-5 py-3">
                <span className="w-28 shrink-0 text-caption text-muted-foreground">{t.token}</span>
                <span className="w-20 shrink-0 text-caption tabular-nums text-muted-foreground">
                  {t.px}
                </span>
                <span className={cn(t.cls, "truncate font-medium text-foreground")}>
                  Settle up with the group
                </span>
              </div>
            ))}
          </div>
        </Section>

        <Section title="Money type" note="Bold, tight, tabular. Figures are the loudest thing.">
          <div className="panel flex flex-wrap items-end gap-x-10 gap-y-6 p-6">
            <div>
              <p className="eyebrow">amount-xl</p>
              <p className="amount-xl text-positive">+1,250 EGP</p>
            </div>
            <div>
              <p className="eyebrow">amount-lg</p>
              <p className="amount-lg text-negative">−340 EGP</p>
            </div>
            <div>
              <p className="eyebrow">amount-md</p>
              <p className="amount-md text-foreground">900 EGP</p>
            </div>
            <div>
              <p className="eyebrow">amount-sm</p>
              <p className="amount-sm text-neutral">0 EGP</p>
            </div>
          </div>
        </Section>

        <Section title="Radius & elevation">
          <Row label="Radius">
            {RADII.map((r) => (
              <div key={r} className="space-y-1.5 text-center">
                <div className={cn("size-16 border border-border bg-card shadow-xs", r)} />
                <p className="text-caption text-muted-foreground">{r}</p>
              </div>
            ))}
          </Row>
          <Row label="Elevation">
            {SHADOWS.map((s) => (
              <div key={s} className="space-y-1.5 text-center">
                <div className={cn("size-16 rounded-xl border border-border bg-card", s)} />
                <p className="text-caption text-muted-foreground">{s}</p>
              </div>
            ))}
          </Row>
        </Section>

        <Section title="Buttons">
          <Row label="Variants">
            <Button>Default</Button>
            <Button variant="secondary">Secondary</Button>
            <Button variant="outline">Outline</Button>
            <Button variant="ghost">Ghost</Button>
            <Button variant="destructive">Destructive</Button>
            <Button variant="link">Link</Button>
          </Row>
          <Row label="Sizes">
            <Button size="sm">Small</Button>
            <Button size="default">Default</Button>
            <Button size="icon" aria-label="Add">
              +
            </Button>
            <Button size="icon-sm" aria-label="Add">
              +
            </Button>
            <Button disabled>Disabled</Button>
            <Button variant="outline" disabled>
              Disabled
            </Button>
          </Row>
          <Row label="Primary CTA — full-width pill">
            <div className="w-full max-w-sm space-y-3">
              <Button size="lg" className="w-full">
                Create group
              </Button>
              <Button size="xl" className="w-full">
                Mark as paid
              </Button>
            </div>
          </Row>
        </Section>

        <Section title="Status pills" note="Colour is never the only signal — every pill is labelled.">
          <Row label="Financial status">
            <Badge variant="positive">You are owed</Badge>
            <Badge variant="negative">You owe</Badge>
            <Badge variant="neutral">Settled</Badge>
          </Row>
          <Row label="Other">
            <Badge>Default</Badge>
            <Badge variant="secondary">Secondary</Badge>
            <Badge variant="outline">Outline</Badge>
            <Badge variant="destructive">Destructive</Badge>
          </Row>
        </Section>

        <Section title="Inputs">
          <div className="grid max-w-md gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="preview-desc" className="text-label font-semibold">
                Description
              </Label>
              <Input id="preview-desc" placeholder="Dinner at Sequoia" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="preview-invalid" className="text-label font-semibold">
                Invalid
              </Label>
              <Input id="preview-invalid" aria-invalid="true" defaultValue="900,," />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="preview-disabled" className="text-label font-semibold">
                Disabled
              </Label>
              <Input id="preview-disabled" disabled defaultValue="Locked after settling" />
            </div>
          </div>
        </Section>

        <Section title="Tabs & avatars">
          <Tabs defaultValue="manual" className="max-w-md">
            <TabsList className="w-full">
              <TabsTrigger value="manual">Manual</TabsTrigger>
              <TabsTrigger value="ai">Describe it</TabsTrigger>
              <TabsTrigger value="receipt">Receipt</TabsTrigger>
            </TabsList>
            <TabsContent value="manual" className="text-body text-muted-foreground">
              Manual entry form goes here.
            </TabsContent>
            <TabsContent value="ai" className="text-body text-muted-foreground">
              Natural-language entry goes here.
            </TabsContent>
            <TabsContent value="receipt" className="text-body text-muted-foreground">
              Receipt upload goes here.
            </TabsContent>
          </Tabs>
          <Row label="Avatars">
            <Avatar className="size-8">
              <AvatarFallback>NI</AvatarFallback>
            </Avatar>
            <Avatar>
              <AvatarFallback>AH</AvatarFallback>
            </Avatar>
            <Avatar className="size-12">
              <AvatarFallback>YM</AvatarFallback>
            </Avatar>
            <div className="flex -space-x-2">
              {["NI", "AH", "YM", "MK"].map((initials) => (
                <Avatar key={initials} className="size-8 ring-2 ring-card">
                  <AvatarFallback>{initials}</AvatarFallback>
                </Avatar>
              ))}
            </div>
          </Row>
        </Section>

        <Section title="Surfaces" note="`panel` is the one card treatment; `panel-inset` recesses detail inside it.">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="panel space-y-3 p-6">
              <p className="text-lead font-bold">panel</p>
              <p className="text-body text-muted-foreground">
                Soft sheet, visible shadow, generous padding.
              </p>
              <div className="panel-inset space-y-1 p-4">
                <p className="eyebrow">panel-inset</p>
                <p className="text-body text-muted-foreground">Read-only detail, recessed.</p>
              </div>
            </div>
            <Card>
              <CardHeader>
                <CardTitle>Alexandria trip</CardTitle>
                <CardDescription>3 members · 12 expenses</CardDescription>
              </CardHeader>
              <CardContent className="flex items-center justify-between">
                <div>
                  <p className="eyebrow">Your balance</p>
                  <p className="amount-md text-negative">−120 EGP</p>
                </div>
                <Badge variant="negative">You owe</Badge>
              </CardContent>
            </Card>
          </div>
        </Section>

        <Section title="Money patterns" note="How the pieces compose on a real screen.">
          <div className="grid gap-4 sm:grid-cols-2">
            {/* Balance hero: quiet label, loud figure, quiet context. */}
            <div className="panel space-y-5 p-6">
              <div>
                <p className="eyebrow">Net position</p>
                <p className="amount-xl text-positive">+125 EGP</p>
                <p className="text-caption text-muted-foreground">across 3 groups</p>
              </div>
              <div className="h-px bg-border" />
              <ul className="space-y-3">
                {SETTLEMENTS.map((s) => (
                  <li key={s.from + s.to} className="flex items-center gap-2 text-label">
                    <span
                      className={cn(
                        "size-1.5 shrink-0 rounded-full",
                        s.owed ? "bg-positive-strong" : "bg-negative-strong",
                      )}
                      aria-hidden
                    />
                    <span className="truncate font-semibold text-foreground">{s.from}</span>
                    <ArrowRight className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
                    <span className="truncate font-semibold text-foreground">{s.to}</span>
                    <span
                      className={cn(
                        "ml-auto font-bold tabular-nums",
                        s.owed ? "text-positive" : "text-negative",
                      )}
                    >
                      {s.amount} EGP
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            {/* Segmented bar: one row, proportional, legend carries the labels. */}
            <div className="panel space-y-5 p-6">
              <div>
                <p className="eyebrow">Group spend</p>
                <p className="amount-lg text-foreground">4,820 EGP</p>
              </div>
              <div
                className="flex h-2.5 w-full overflow-hidden rounded-full"
                role="img"
                aria-label="Spend by category: food 34%, transport 21%, entertainment 15%, shopping 12%, stays 9%, utilities 6%, other 3%."
              >
                {CATEGORIES.map((c) => (
                  <span
                    key={c.token}
                    style={
                      {
                        width: `${c.share}%`,
                        background: `var(${c.token})`,
                      } satisfies CSSProperties
                    }
                  />
                ))}
              </div>
              <ul className="space-y-2">
                {CATEGORIES.slice(0, 4).map((c) => (
                  <li key={c.token} className="flex items-center gap-2 text-label">
                    <span className="category-dot" style={{ background: `var(${c.token})` }} />
                    <span className="font-medium text-foreground">{c.name}</span>
                    <span className="ml-auto font-semibold tabular-nums text-muted-foreground">
                      {c.share}%
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </Section>

        <Section title="Rows" note="`row-hover` gives every clickable list row the same feedback.">
          <div className="panel overflow-hidden">
            {SETTLEMENTS.map((s) => (
              <button
                key={s.from + s.to}
                type="button"
                className="row-hover flex w-full items-center gap-3 border-b border-border px-5 py-4 text-left last:border-b-0"
              >
                <Avatar className="size-9">
                  <AvatarFallback>{s.from.slice(0, 2).toUpperCase()}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-body font-semibold text-foreground">
                    {s.from} → {s.to}
                  </p>
                  <p className="text-caption text-muted-foreground">Tap to settle</p>
                </div>
                <span
                  className={cn("amount-sm", s.owed ? "text-positive" : "text-negative")}
                >
                  {s.amount}
                </span>
              </button>
            ))}
          </div>
        </Section>
      </div>
    </div>
  );
}
