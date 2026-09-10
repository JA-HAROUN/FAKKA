import {
  Utensils,
  Car,
  Popcorn,
  ShoppingBag,
  BedDouble,
  Plug,
  Receipt,
  type LucideIcon,
} from "lucide-react";
import type { CategoryId } from "@/types";

export interface CategoryMeta {
  id: CategoryId;
  label: string;
  icon: LucideIcon;
}

export const CATEGORIES: CategoryMeta[] = [
  { id: "food", label: "Food", icon: Utensils },
  { id: "transportation", label: "Transportation", icon: Car },
  { id: "entertainment", label: "Entertainment", icon: Popcorn },
  { id: "shopping", label: "Shopping", icon: ShoppingBag },
  { id: "accommodation", label: "Accommodation", icon: BedDouble },
  { id: "utilities", label: "Utilities", icon: Plug },
  { id: "other", label: "Other", icon: Receipt },
];

const OTHER: CategoryMeta = { id: "other", label: "Other", icon: Receipt };

export function getCategory(id: CategoryId): CategoryMeta {
  return CATEGORIES.find((c) => c.id === id) ?? OTHER;
}
