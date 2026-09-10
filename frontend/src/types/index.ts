export type CategoryId =
  | "food"
  | "transportation"
  | "entertainment"
  | "shopping"
  | "accommodation"
  | "utilities"
  | "other";

export type SplitType = "equal" | "custom";

export interface User {
  id: string;
  name: string;
  email: string;
  avatar: string; // emoji or initials
}

export interface Group {
  id: string;
  name: string;
  image: string; // emoji
  members: string[]; // user ids
  createdAt: string; // ISO
}

export interface PurchasedItem {
  id: string;
  name: string;
  quantity: number;
  price: number;
  assignedTo: string[]; // user ids
}

export interface Expense {
  id: string;
  groupId: string;
  category: CategoryId;
  description: string;
  image?: string;
  totalAmount: number;
  paidBy: string;
  participants: string[];
  splitType: SplitType;
  shares: Record<string, number>;
  items?: PurchasedItem[];
  createdAt: string; // ISO
  source?: "manual" | "ai" | "receipt";
}

export interface Settlement {
  id: string;
  groupId: string;
  fromUser: string;
  toUser: string;
  amount: number;
  status: "pending" | "paid";
  paidAt?: string;
}

/** Draft used across the three Add Expense entry methods. */
export interface ExpenseDraft {
  category: CategoryId;
  description: string;
  image?: string;
  totalAmount: number;
  paidBy: string;
  participants: string[];
  splitType: SplitType;
  shares: Record<string, number>;
  items?: PurchasedItem[];
  source: "manual" | "ai" | "receipt";
}
