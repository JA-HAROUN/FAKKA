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
    <div className="space-y-4">
      <Link
        to="/dashboard"
        className="-mx-1.5 inline-flex min-h-8 items-center gap-1.5 rounded-md px-1.5 text-[13px] font-medium text-muted-foreground transition-colors hover:bg-surface hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" aria-hidden /> All groups
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-4">
        <div className="flex min-w-0 items-start gap-3 sm:gap-4">
          <span
            className="grid size-12 shrink-0 place-items-center rounded-xl border border-border bg-surface text-2xl"
            aria-hidden
          >
            {group.image}
          </span>
          <div className="min-w-0 space-y-1.5">
            <h1 className="truncate text-xl font-semibold tracking-tight sm:text-2xl">
              {group.name}
            </h1>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
              <AvatarStack users={members} max={5} />
              <span className="text-xs text-muted-foreground">
                {pluralize(group.members.length, "member")} · created{" "}
                {formatShortDate(group.createdAt)}
              </span>
            </div>
          </div>
        </div>

        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>

      <div className="panel grid gap-5 p-4 sm:grid-cols-2 sm:p-5">
        <div>
          <BalanceStatement amount={myBalance} />
          <p className="mt-0.5 text-xs text-muted-foreground">in this group</p>
        </div>
        <div className="space-y-0.5 sm:border-l sm:border-border sm:pl-5">
          <p className="text-xs font-medium text-muted-foreground">Total group spending</p>
          <p>
            <Money value={total} size="xl" />
          </p>
          <p className="text-xs text-muted-foreground">
            Across {pluralize(group.members.length, "member")}
          </p>
        </div>
      </div>
    </div>
  );
}
