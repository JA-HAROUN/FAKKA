import { useMemo, useState } from "react";
import { Check, Search, UserPlus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ResponsiveModal } from "@/components/common/ResponsiveModal";
import { UserAvatar } from "@/components/common/UserAvatar";
import { useApp } from "@/context/AppContext";
import { discoverableUsers } from "@/utils/mockData";

/** Find registered people by name or email and add them as a friend. */
export function AddFriendDialog() {
  const { addFriend, friendIds, currentUser } = useApp();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return discoverableUsers.filter(
      (u) =>
        u.id !== currentUser?.id &&
        (u.email.toLowerCase().includes(q) || u.name.toLowerCase().includes(q)),
    );
  }, [query, currentUser]);

  return (
    <ResponsiveModal
      open={open}
      onOpenChange={(v) => {
        setOpen(v);
        if (!v) setQuery("");
      }}
      trigger={
        <Button>
          <UserPlus aria-hidden /> Add friend
        </Button>
      }
      title="Add a friend"
      description="Search registered people by name or email address."
    >
      <div className="space-y-3">
        <div className="relative">
          <Search
            className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden
          />
          <Input
            autoFocus
            className="pl-9"
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="omar@fakka.app"
            aria-label="Search people"
          />
        </div>

        <div className="min-h-32">
          {!query.trim() && (
            <p className="px-1 py-8 text-center text-sm text-muted-foreground">
              Start typing to search.
            </p>
          )}

          {query.trim() && results.length === 0 && (
            <p className="px-1 py-8 text-center text-sm text-muted-foreground">
              No one matches “{query.trim()}”. Check the spelling, or ask them to sign up first.
            </p>
          )}

          {results.length > 0 && (
            <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
              {results.map((user) => {
                const added = friendIds.includes(user.id);
                return (
                  <li key={user.id} className="flex items-center gap-3 px-3 py-2.5">
                    <UserAvatar user={user} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{user.name}</p>
                      <p className="truncate text-xs text-muted-foreground">{user.email}</p>
                    </div>
                    <Button
                      size="sm"
                      variant={added ? "secondary" : "outline"}
                      disabled={added}
                      onClick={() => {
                        addFriend(user);
                        toast.success(`${user.name} added to your friends`);
                      }}
                    >
                      {added ? (
                        <>
                          <Check aria-hidden /> Added
                        </>
                      ) : (
                        "Add"
                      )}
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </ResponsiveModal>
  );
}
