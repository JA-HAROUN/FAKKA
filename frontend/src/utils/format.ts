const DAY_MS = 86_400_000;

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** "Today" / "Yesterday" / "4 days ago" / "12 Aug" / "12 Aug 2025". */
export function formatRelativeDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const days = Math.round((startOfDay(new Date()) - startOfDay(date)) / DAY_MS);

  if (days === 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days > 1 && days < 7) return `${days} days ago`;
  return formatShortDate(iso);
}

/** "12 Aug", with the year appended once it is no longer the current one. */
export function formatShortDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    ...(sameYear ? {} : { year: "numeric" }),
  });
}

/** Heading for a day-grouped list: "Today", "Yesterday", "Friday 28 Aug". */
export function formatDayHeading(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const days = Math.round((startOfDay(new Date()) - startOfDay(date)) / DAY_MS);
  if (days === 0) return "Today";
  if (days === 1) return "Yesterday";
  const sameYear = date.getFullYear() === new Date().getFullYear();
  return date.toLocaleDateString("en-GB", {
    weekday: "long",
    day: "numeric",
    month: "short",
    ...(sameYear ? {} : { year: "numeric" }),
  });
}

/** Stable key for grouping ISO timestamps by calendar day. */
export function dayKey(iso: string): string {
  return iso.slice(0, 10);
}

export function firstName(name: string): string {
  return name.split(/\s+/)[0] ?? name;
}

/** Grouped thousands, up to two decimals, no currency suffix. */
export function formatNumber(value: number): string {
  return value.toLocaleString("en-US", { maximumFractionDigits: 2 });
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}
