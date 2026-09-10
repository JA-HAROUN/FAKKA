import { createFileRoute } from "@tanstack/react-router";
import { RequireAuth } from "@/components/common/RequireAuth";
import { FriendList } from "@/components/friends/FriendList";

export const Route = createFileRoute("/friends")({
  head: () => ({
    meta: [
      { title: "Friends — Fakka" },
      {
        name: "description",
        content: "Find friends by email or name and add them to your expense groups.",
      },
      { property: "og:title", content: "Friends — Fakka" },
      {
        property: "og:description",
        content: "Manage the people you split shared expenses with.",
      },
    ],
  }),
  component: () => (
    <RequireAuth>
      <FriendList />
    </RequireAuth>
  ),
});
