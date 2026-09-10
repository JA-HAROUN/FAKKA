import { Skeleton } from "@/components/ui/skeleton";
import { Panel } from "@/components/common/Section";

/** Rows inside a flush panel — matches the height of a real list row. */
function ListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <Panel flush>
      <ul className="divide-y divide-border">
        {Array.from({ length: rows }).map((_, i) => (
          <li key={i} className="flex items-center gap-3 p-4">
            <Skeleton className="size-9 rounded-full" />
            <div className="min-w-0 flex-1 space-y-2">
              <Skeleton className="h-3.5 w-40 max-w-[60%]" />
              <Skeleton className="h-3 w-24 max-w-[40%]" />
            </div>
            <Skeleton className="h-3.5 w-16" />
          </li>
        ))}
      </ul>
    </Panel>
  );
}

/** Full-page placeholder used while persisted state is being restored. */
export function PageSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-live="polite">
      <span className="sr-only">Loading</span>
      <div className="space-y-2">
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-4 w-64 max-w-full" />
      </div>
      <Panel className="space-y-4">
        <Skeleton className="h-3 w-28" />
        <Skeleton className="h-8 w-40" />
        <div className="grid gap-4 sm:grid-cols-2">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      </Panel>
      <ListSkeleton rows={3} />
    </div>
  );
}
