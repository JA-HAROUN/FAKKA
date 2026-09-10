import { useMemo, useState } from "react";
import { Search, UserRoundPlus } from "lucide-react";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/common/EmptyState";
import { BalanceIndicator } from "@/components/common/Money";
import { PageHeader, Panel, PanelList } from "@/components/common/Section";
import { UserAvatar } from "@/components/common/UserAvatar";
import { AddFriendDialog } from "@/components/friends/AddFriendDialog";
import { useApp } from "@/context/AppContext";
import { useGroupSummaries } from "@/hooks/useGroupSummaries";
import { pluralize } from "@/utils/format";

/**
 * Friends, each with the net balance between them and the current user across
 * every shared group — the page answers "where do I stand with this person",
 * not just "who is on my list".
 */
export function FriendList() {
  const { friends } = useApp();
  const { summaries, people } = useGroupSummaries();
  const [query, setQuery] = useState("");

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return friends
      .filter((f) => !q || f.name.toLowerCase().includes(q) || f.email.toLowerCase().includes(q))
      .map((friend) => ({
        friend,
        balance: people.find((p) => p.userId === friend.id)?.amount ?? 0,
        sharedGroups: summaries.filter((s) => s.group.members.includes(friend.id)).length,
      }));
  }, [friends, people, query, summaries]);

  const showSearch = friends.length > 5;

  return (
    // A single list of people reads badly at full desktop width — cap the
    // measure so the name and the balance stay visually connected.
    <div className="max-w-3xl space-y-6">
      <PageHeader
        title="Friends"
        description={
          friends.length === 0
            ? "The people you split expenses with live here."
            : `${pluralize(friends.length, "person", "people")} you can split expenses with.`
        }
        {...(friends.length > 0 ? { actions: <AddFriendDialog /> } : {})}
      />

      {friends.length === 0 ? (
        <EmptyState
          icon={UserRoundPlus}
          title="No friends yet"
          description="Add people by name or email to start creating groups and splitting expenses."
          action={<AddFriendDialog />}
        />
      ) : (
        <div className="space-y-3">
          {showSearch && (
            <div className="relative max-w-sm">
              <Search
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
                aria-hidden
              />
              <Input
                className="pl-9"
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search friends"
                aria-label="Search friends"
              />
            </div>
          )}

          {rows.length === 0 ? (
            <EmptyState
              icon={Search}
              title="No matches"
              description={`Nobody on your list matches “${query.trim()}”.`}
            />
          ) : (
            <Panel flush>
              <PanelList>
                {rows.map(({ friend, balance, sharedGroups }) => (
                  <li key={friend.id} className="flex items-center gap-3 p-4">
                    <UserAvatar user={friend} size="md" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{friend.name}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {friend.email}
                        {sharedGroups > 0 && ` · ${pluralize(sharedGroups, "shared group")}`}
                      </p>
                    </div>
                    <BalanceIndicator
                      amount={balance}
                      label={
                        balance > 0.005 ? "owes you" : balance < -0.005 ? "you owe" : "settled"
                      }
                    />
                  </li>
                ))}
              </PanelList>
            </Panel>
          )}
        </div>
      )}
    </div>
  );
}
