import { useMemo, useState } from "react";
import { Search, UserPlus, Check } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { UserAvatar } from "@/components/common/UserAvatar";
import { EmptyState } from "@/components/common/EmptyState";
import { useApp } from "@/context/AppContext";
import { discoverableUsers } from "@/utils/mockData";

export function FriendList() {
  const { friends, addFriend, friendIds, currentUser } = useApp();
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
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Friends</h2>
          <p className="text-sm text-muted-foreground">
            {friends.length} {friends.length === 1 ? "person" : "people"} you can split with
          </p>
        </div>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button className="rounded-full">
              <UserPlus className="size-4" /> Add friend
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>Add a friend</DialogTitle>
              <DialogDescription>Search by name or email address.</DialogDescription>
            </DialogHeader>
            <div className="relative">
              <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                autoFocus
                className="pl-9"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="omar@splitease.app"
              />
            </div>
            <div className="min-h-24 space-y-1">
              {query.trim() && results.length === 0 && (
                <p className="py-6 text-center text-sm text-muted-foreground">
                  No one found for “{query}”.
                </p>
              )}
              {results.map((u) => {
                const added = friendIds.includes(u.id);
                return (
                  <div key={u.id} className="flex items-center gap-3 rounded-xl px-2 py-2">
                    <UserAvatar user={u} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{u.name}</p>
                      <p className="truncate text-xs text-muted-foreground">{u.email}</p>
                    </div>
                    <Button
                      size="sm"
                      variant={added ? "secondary" : "default"}
                      disabled={added}
                      onClick={() => {
                        addFriend(u);
                        toast.success(`${u.name} added to your friends`);
                      }}
                    >
                      {added ? <Check className="size-4" /> : "Add"}
                    </Button>
                  </div>
                );
              })}
            </div>
          </DialogContent>
        </Dialog>
      </div>

      {friends.length === 0 ? (
        <EmptyState
          emoji="🫂"
          title="No friends yet"
          description="Add people by email to start creating groups and splitting expenses."
        />
      ) : (
        <ul className="card-soft divide-y divide-border">
          {friends.map((f) => (
            <li key={f.id} className="flex items-center gap-3 p-4">
              <UserAvatar user={f} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{f.name}</p>
                <p className="truncate text-sm text-muted-foreground">{f.email}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
