import { cn } from "@/lib/utils";
import type { User } from "@/types";

const sizes = {
  xs: "size-6 text-[11px]",
  sm: "size-8 text-xs",
  md: "size-9 text-sm",
  lg: "size-12 text-base",
} as const;

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

/** Emoji avatars come from the user record; anything else falls back to initials. */
export function UserAvatar({
  user,
  size = "md",
  className,
}: {
  user: User;
  size?: keyof typeof sizes;
  className?: string;
}) {
  const isEmoji = Boolean(user.avatar) && !/^[a-z0-9\s]+$/i.test(user.avatar);
  return (
    <span
      aria-hidden
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full border border-border bg-surface font-medium text-muted-foreground select-none",
        sizes[size],
        className,
      )}
    >
      {isEmoji ? user.avatar : initials(user.name)}
    </span>
  );
}

export function AvatarStack({
  users,
  max = 4,
  size = "sm",
  className,
}: {
  users: User[];
  max?: number;
  size?: keyof typeof sizes;
  className?: string;
}) {
  const shown = users.slice(0, max);
  const rest = users.length - shown.length;
  return (
    <div className={cn("flex items-center -space-x-1.5", className)}>
      {shown.map((u) => (
        <UserAvatar key={u.id} user={u} size={size} className="ring-2 ring-card" />
      ))}
      {rest > 0 && (
        <span
          className={cn(
            "inline-flex items-center justify-center rounded-full border border-border bg-surface font-medium text-muted-foreground ring-2 ring-card",
            sizes[size],
          )}
        >
          +{rest}
        </span>
      )}
      <span className="sr-only">{users.map((u) => u.name).join(", ")}</span>
    </div>
  );
}
