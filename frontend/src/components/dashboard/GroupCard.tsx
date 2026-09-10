import { Link } from "@tanstack/react-router";
import { Users } from "lucide-react";
import { AvatarStack } from "@/components/common/UserAvatar";
import { BalancePill } from "@/components/common/BalancePill";
import type { Group } from "@/types";
import { useApp } from "@/context/AppContext";
import { groupBalances } from "@/utils/calculations";

export function GroupCard({ group }: { group: Group }) {
  const { userById, currentUser, expensesOfGroup, settlementsOfGroup } = useApp();
  const balances = groupBalances(
    group.members,
    expensesOfGroup(group.id),
    settlementsOfGroup(group.id),
  );
  const myBalance = currentUser ? (balances[currentUser.id] ?? 0) : 0;

  return (
    <Link
      to="/group/$groupId"
      params={{ groupId: group.id }}
      className="card-soft group flex flex-col gap-4 p-5 transition-all hover:-translate-y-0.5 hover:shadow-[var(--shadow-lift)]"
    >
      <div className="flex items-start gap-3">
        <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-primary-soft text-2xl">
          {group.image}
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="truncate font-semibold">{group.name}</h3>
          <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
            <Users className="size-3.5" />
            {group.members.length} members
          </p>
        </div>
      </div>

      <BalancePill amount={myBalance} className="self-start" />

      <div className="flex items-center justify-between">
        <AvatarStack users={group.members.map(userById)} />
        <span className="text-xs text-muted-foreground">
          Since {new Date(group.createdAt).toLocaleDateString()}
        </span>
      </div>
    </Link>
  );
}
