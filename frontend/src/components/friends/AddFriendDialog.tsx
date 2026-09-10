import { ResponsiveModal } from "@/components/common/ResponsiveModal";
import { UserAvatar } from "@/components/common/UserAvatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useApp } from "@/context/AppContext";
import { Check, Search, UserPlus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

/** Find registered people by name or email and add them as a friend. */
export function AddFriendDialog() {
  const { addFriend, friendIds } = useApp();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  const result = query.trim()
    ? { id: "lookup", name: query.trim(), email: query.includes("@") ? query.trim() : "", avatar: query.trim() }
    : null;

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

          {query.trim() && !result && (
            <p className="px-1 py-8 text-center text-sm text-muted-foreground">
              No one matches “{query.trim()}”. Check the spelling, or ask them to sign up first.
            </p>
          )}

          {result && (
            <ul className="divide-y divide-border overflow-hidden rounded-lg border border-border">
              {[result].map((user) => {
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
                        void addFriend(user)
                          .then(() => toast.success(`${user.name} added to your friends`))
                          .catch((error: unknown) => toast.error(error instanceof Error ? error.message : "Could not add friend"));
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
