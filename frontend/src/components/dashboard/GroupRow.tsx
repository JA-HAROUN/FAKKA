import { Link } from "@tanstack/react-router";
import { ChevronRight } from "lucide-react";
import { AvatarStack } from "@/components/common/UserAvatar";
import { BalanceIndicator } from "@/components/common/Money";
import { useApp } from "@/context/AppContext";
import { formatAmount } from "@/utils/calculations";
import { formatRelativeDate, pluralize } from "@/utils/format";
import type { GroupSummary } from "@/utils/insights";

/**
 * A group as a list row rather than a card: the same facts (name, icon,
 * members, spend, the user's balance) at a density where several groups can be
 * compared at a glance.
 */
export function GroupRow({ summary }: { summary: GroupSummary }) {
  const { userById } = useApp();
  const { group, total, myBalance, pending } = summary;

  return (
    <li>
      <Link
        to="/group/$groupId"
        params={{ groupId: group.id }}
        className="row-hover flex items-center gap-3 p-4 sm:gap-4"
      >
        <span
          className="grid size-10 shrink-0 place-items-center rounded-lg border border-border bg-surface text-lg"
          aria-hidden
        >
          {group.image}
        </span>

        <div className="min-w-0 flex-1">
          <h3 className="text-sm font-semibold">{group.name}</h3>
          <p className="mt-0.5 truncate text-xs text-muted-foreground">
            {pluralize(group.members.length, "member")} · {formatAmount(total)} spent ·{" "}
            {formatRelativeDate(summary.lastActivity)}
          </p>
          <div className="mt-2 sm:hidden">
            <BalanceIndicator amount={myBalance} />
          </div>
        </div>

        <div className="hidden shrink-0 items-center gap-3 sm:flex lg:gap-4">
          <AvatarStack users={group.members.map(userById)} max={4} className="hidden xl:flex" />
          <BalanceIndicator amount={myBalance} />
        </div>

        <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      </Link>
      {pending.length > 0 && (
        <span className="sr-only">
          {pending.length} outstanding {pending.length === 1 ? "transfer" : "transfers"}
        </span>
      )}
    </li>
  );
}
