import { Link } from "@tanstack/react-router";
import { ChevronRight } from "lucide-react";
import { AvatarStack } from "@/components/common/UserAvatar";
import { BalanceStatement } from "@/components/common/Money";
import { useApp } from "@/context/AppContext";
import { formatAmount } from "@/utils/calculations";
import { formatRelativeDate, pluralize } from "@/utils/format";
import type { GroupSummary } from "@/utils/insights";

/**
 * A group as a card: identity at the top, the user's balance as the loudest
 * thing in the tile, and the supporting facts (members, spend, last activity)
 * kept quiet around it. Cards sit in a grid rather than a divided list so each
 * group gets room for a full-size figure instead of a compressed chip.
 */
export function GroupCard({ summary }: { summary: GroupSummary }) {
  const { userById } = useApp();
  const { group, total, myBalance, pending } = summary;

  return (
    <li className="min-w-0">
      <Link
        to="/group/$groupId"
        params={{ groupId: group.id }}
        className="panel flex h-full flex-col gap-5 p-5 transition-shadow hover:shadow-md"
      >
        <div className="flex items-start gap-3">
          <span
            className="grid size-11 shrink-0 place-items-center rounded-lg border border-border bg-surface text-lg"
            aria-hidden
          >
            {group.image}
          </span>
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-lead font-bold tracking-tight">{group.name}</h3>
            <p className="truncate text-caption text-muted-foreground">
              {pluralize(group.members.length, "member")} · {formatAmount(total)} spent
            </p>
          </div>
          <ChevronRight className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
        </div>

        <div className="mt-auto space-y-4">
          <div className="flex items-end justify-between gap-3">
            <BalanceStatement amount={myBalance} size="xl" />
            <AvatarStack users={group.members.map(userById)} max={4} />
          </div>
          <p className="text-caption text-muted-foreground">
            {formatRelativeDate(summary.lastActivity)}
          </p>
        </div>
      </Link>
      {pending.length > 0 && (
        <span className="sr-only">
          {pending.length} outstanding {pending.length === 1 ? "transfer" : "transfers"}
        </span>
      )}
    </li>
  );
}
