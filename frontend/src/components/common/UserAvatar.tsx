import { cn } from "@/lib/utils";
import type { User } from "@/types";

const sizes = {
  sm: "size-8 text-sm",
  md: "size-10 text-base",
  lg: "size-14 text-2xl",
};

export function UserAvatar({
  user,
  size = "md",
  className,
}: {
  user: User;
  size?: keyof typeof sizes;
  className?: string;
}) {
  return (
    <span
      title={user.name}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full bg-primary-soft ring-2 ring-card select-none",
        sizes[size],
        className,
      )}
    >
      {user.avatar}
    </span>
  );
}

export function AvatarStack({ users, max = 4 }: { users: User[]; max?: number }) {
  const shown = users.slice(0, max);
  const rest = users.length - shown.length;
  return (
    <div className="flex items-center -space-x-2">
      {shown.map((u) => (
        <UserAvatar key={u.id} user={u} size="sm" />
      ))}
      {rest > 0 && (
        <span className="inline-flex size-8 items-center justify-center rounded-full bg-muted text-xs font-medium text-muted-foreground ring-2 ring-card">
          +{rest}
        </span>
      )}
    </div>
  );
}
