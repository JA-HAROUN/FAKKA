import { useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { UserAvatar } from "@/components/common/UserAvatar";
import { useApp } from "@/context/AppContext";
import { cn } from "@/lib/utils";

const EMOJIS = ["🍝", "🐫", "🏠", "✈️", "🎉", "🏖️", "☕", "🎬", "🛒", "⚽", "🎓", "🚗"] as const;

export function CreateGroupDialog() {
  const { friends, currentUser, createGroup } = useApp();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [image, setImage] = useState<string>(EMOJIS[0]);
  const [selected, setSelected] = useState<string[]>([]);
  const [error, setError] = useState("");

  function reset() {
    setName("");
    setImage(EMOJIS[0]);
    setSelected([]);
    setError("");
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        setOpen(v);
        if (!v) reset();
      }}
    >
      <DialogTrigger asChild>
        <Button size="lg" className="rounded-full">
          <Plus className="size-4" /> Create group
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Create a group</DialogTitle>
          <DialogDescription>Pick a name, an icon and who's in it.</DialogDescription>
        </DialogHeader>

        <div className="space-y-5">
          <div className="space-y-2">
            <Label htmlFor="group-name">Group name</Label>
            <Input
              id="group-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Weekend in Dahab"
            />
          </div>

          <div className="space-y-2">
            <Label>Group icon</Label>
            <div className="flex flex-wrap gap-2">
              {EMOJIS.map((e) => (
                <button
                  key={e}
                  type="button"
                  onClick={() => setImage(e)}
                  className={cn(
                    "grid size-11 place-items-center rounded-xl border text-xl transition-colors",
                    image === e ? "border-primary bg-primary-soft" : "border-border bg-card",
                  )}
                >
                  {e}
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-2">
            <Label>Add friends</Label>
            {friends.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No friends yet — add some from the Friends page first.
              </p>
            ) : (
              <div className="space-y-1">
                {friends.map((f) => (
                  <label
                    key={f.id}
                    className="flex cursor-pointer items-center gap-3 rounded-xl px-2 py-2 hover:bg-secondary"
                  >
                    <Checkbox
                      checked={selected.includes(f.id)}
                      onCheckedChange={(v) =>
                        setSelected((prev) =>
                          v ? [...prev, f.id] : prev.filter((id) => id !== f.id),
                        )
                      }
                    />
                    <UserAvatar user={f} size="sm" />
                    <span className="text-sm font-medium">{f.name}</span>
                  </label>
                ))}
              </div>
            )}
          </div>

          {error && <p className="text-sm text-negative">{error}</p>}
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button
            onClick={() => {
              if (!name.trim()) {
                setError("Give your group a name.");
                return;
              }
              if (selected.length === 0) {
                setError("Pick at least one friend.");
                return;
              }
              const group = createGroup({
                name: name.trim(),
                image,
                memberIds: [currentUser!.id, ...selected],
              });
              setOpen(false);
              reset();
              toast.success(`"${group.name}" created`);
              navigate({ to: "/group/$groupId", params: { groupId: group.id } });
            }}
          >
            Create group
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
