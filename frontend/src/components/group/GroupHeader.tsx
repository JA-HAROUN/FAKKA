import { Link } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import type { ReactNode } from "react";
import { AvatarStack } from "@/components/common/UserAvatar";
import { BalanceStatement, Money } from "@/components/common/Money";
import type { Group } from "@/types";
import { useApp } from "@/context/AppContext";
import { formatShortDate, pluralize } from "@/utils/format";

/**
 * Group identity and financial position in one block: what the group is, what
 * it has spent, and where the current user stands in it.
 */
export function GroupHeader({
  group,
  total,
  myBalance,
  actions,
}: {
  group: Group;
  total: number;
  myBalance: number;
  actions?: ReactNode;
}) {
  const { userById } = useApp();
  const members = group.members.map(userById);

  return (
    <div className="space-y-5">
      <Link
        to="/dashboard"
        className="-mx-1.5 inline-flex min-h-8 items-center gap-1.5 rounded-md px-1.5 text-label font-medium text-muted-foreground transition-colors hover:bg-surface hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" aria-hidden /> All groups
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-4">
        <div className="flex min-w-0 items-start gap-3 sm:gap-4">
          <span
            className="grid size-12 shrink-0 place-items-center rounded-lg border border-border bg-surface text-2xl"
            aria-hidden
          >
            {group.image}
          </span>
          <div className="min-w-0 space-y-2">
            <h1 className="truncate text-title font-extrabold tracking-tight sm:text-display">
              {group.name}
            </h1>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
              <AvatarStack users={members} max={5} />
              <span className="text-caption text-muted-foreground">
                {pluralize(group.members.length, "member")} · created{" "}
                {formatShortDate(group.createdAt)}
              </span>
            </div>
          </div>
        </div>

        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>

      {/* Same hero rhythm as the dashboard's position summary, so the two screens
          read as one product: the reader's own balance is the loudest figure, and
          the group-level total sits recessed beside it rather than across a rule. */}
      <div className="panel p-5 sm:p-6">
        <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-10">
          <div className="space-y-1.5">
            <BalanceStatement amount={myBalance} size="2xl" />
            <p className="text-body text-muted-foreground">in this group</p>
          </div>

          <dl className="panel-inset p-4 md:min-w-[15rem]">
            <dt className="eyebrow">Total group spending</dt>
            <dd className="mt-2">
              <Money value={total} size="xl" />
              <span className="mt-0.5 block text-caption text-muted-foreground">
                Across {pluralize(group.members.length, "member")}
              </span>
            </dd>
          </dl>
        </div>
      </div>
    </div>
  );
}
