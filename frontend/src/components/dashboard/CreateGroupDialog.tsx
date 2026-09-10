import { useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FormError } from "@/components/common/Field";
import { ResponsiveModal } from "@/components/common/ResponsiveModal";
import { UserAvatar } from "@/components/common/UserAvatar";
import { useApp } from "@/context/AppContext";
import { cn } from "@/lib/utils";
import { pluralize } from "@/utils/format";

const EMOJIS = ["🍝", "🐫", "🏠", "✈️", "🎉", "🏖️", "☕", "🎬", "🛒", "⚽", "🎓", "🚗"] as const;

export function CreateGroupDialog() {
  const { friends, currentUser, createGroup } = useApp();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [image, setImage] = useState<string>(EMOJIS[0]);
  const [selected, setSelected] = useState<string[]>([]);
  const [errors, setErrors] = useState<{ name?: string; members?: string }>({});

  function reset() {
    setName("");
    setImage(EMOJIS[0]);
    setSelected([]);
    setErrors({});
  }

  function submit() {
    const next: typeof errors = {};
    if (!name.trim()) next.name = "Give your group a name.";
    if (selected.length === 0) next.members = "Pick at least one friend to split with.";
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    const group = createGroup({
      name: name.trim(),
      image,
      memberIds: [currentUser!.id, ...selected],
    });
    setOpen(false);
    reset();
    toast.success(`${group.name} created`, {
      description: `${pluralize(group.members.length, "member")} · start adding expenses`,
    });
    void navigate({ to: "/group/$groupId", params: { groupId: group.id } });
  }

  return (
    <ResponsiveModal
      open={open}
      onOpenChange={(v) => {
        setOpen(v);
        if (!v) reset();
      }}
      trigger={
        <Button>
          <Plus className="size-4" aria-hidden /> New group
        </Button>
      }
      title="Create a group"
      description="Name it, pick an icon, and choose who's splitting."
      footer={
        <>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={submit}>Create group</Button>
        </>
      }
    >
      <div className="space-y-5">
        <Field label="Group name" error={errors.name} required>
          {(field) => (
            <Input
              {...field}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Weekend in Dahab"
              autoFocus
            />
          )}
        </Field>

        <fieldset className="space-y-2">
          <legend className="text-[13px] font-medium">Icon</legend>
          <div className="grid grid-cols-6 gap-1.5" role="radiogroup" aria-label="Group icon">
            {EMOJIS.map((emoji) => {
              const active = image === emoji;
              return (
                <button
                  key={emoji}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  aria-label={`Icon ${emoji}`}
                  onClick={() => setImage(emoji)}
                  className={cn(
                    "grid h-10 cursor-pointer place-items-center rounded-lg border text-lg transition-colors",
                    active
                      ? "border-primary bg-primary-soft"
                      : "border-border bg-card hover:bg-surface",
                  )}
                >
                  {emoji}
                </button>
              );
            })}
          </div>
        </fieldset>

        <div className="space-y-2">
          <div className="flex items-baseline justify-between gap-3">
            <Label className="text-[13px] font-medium">Members</Label>
            <span className="text-xs text-muted-foreground">
              {selected.length > 0 ? `${selected.length} selected` : "You're included"}
            </span>
          </div>

          {friends.length === 0 ? (
            <div className="panel-inset px-4 py-5 text-center">
              <p className="text-sm text-muted-foreground">
                You haven't added any friends yet.{" "}
                <Link
                  to="/friends"
                  className="font-medium text-primary hover:underline"
                  onClick={() => setOpen(false)}
                >
                  Add a friend
                </Link>{" "}
                to create a group.
              </p>
            </div>
          ) : (
            <ul className="max-h-60 overflow-y-auto rounded-lg border border-border">
              {friends.map((friend) => {
                const checked = selected.includes(friend.id);
                return (
                  <li key={friend.id} className="border-b border-border last:border-b-0">
                    <label className="row-hover flex cursor-pointer items-center gap-3 px-3 py-2.5">
                      <Checkbox
                        checked={checked}
                        onCheckedChange={(v) =>
                          setSelected((prev) =>
                            v ? [...prev, friend.id] : prev.filter((id) => id !== friend.id),
                          )
                        }
                      />
                      <UserAvatar user={friend} size="sm" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium">{friend.name}</span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {friend.email}
                        </span>
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
          <FormError message={errors.members} />
        </div>
      </div>
    </ResponsiveModal>
  );
}
