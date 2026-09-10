import type { Expense, Group, Settlement, User } from "@/types";
import { equalShares } from "./calculations";

export const CURRENT_USER_ID = "u1";

export const mockUsers: User[] = [
  { id: "u1", name: "John William", email: "john@splitease.app", avatar: "🧑‍💻" },
  { id: "u2", name: "Ahmed Hassan", email: "ahmed@splitease.app", avatar: "🧔" },
  { id: "u3", name: "Mohamed Salah", email: "mohamed@splitease.app", avatar: "⚽" },
  { id: "u4", name: "Nour Ibrahim", email: "nour@splitease.app", avatar: "👩‍🎨" },
  { id: "u5", name: "Layla Fahmy", email: "layla@splitease.app", avatar: "👩‍🔬" },
];

/** Directory of people you can discover by email/username when adding a friend. */
export const discoverableUsers: User[] = [
  { id: "u6", name: "Omar Tarek", email: "omar@splitease.app", avatar: "🎧" },
  { id: "u7", name: "Sara Adel", email: "sara@splitease.app", avatar: "🌷" },
  { id: "u8", name: "Karim Zaki", email: "karim@splitease.app", avatar: "🏀" },
  { id: "u9", name: "Hana Youssef", email: "hana@splitease.app", avatar: "📚" },
];

export const mockGroups: Group[] = [
  {
    id: "g1",
    name: "Friday Dinner Crew",
    image: "🍝",
    members: ["u1", "u2", "u3", "u4"],
    createdAt: "2026-07-12T18:00:00.000Z",
  },
  {
    id: "g2",
    name: "Egypt Road Trip",
    image: "🐫",
    members: ["u1", "u2", "u3", "u4", "u5"],
    createdAt: "2026-08-02T07:30:00.000Z",
  },
  {
    id: "g3",
    name: "Flat 12 Bills",
    image: "🏠",
    members: ["u1", "u5"],
    createdAt: "2026-06-01T09:00:00.000Z",
  },
];

function expense(
  e: Omit<Expense, "shares" | "splitType"> & Partial<Pick<Expense, "shares" | "splitType">>,
): Expense {
  return {
    splitType: "equal",
    shares: e.shares ?? equalShares(e.totalAmount, e.participants),
    ...e,
  } as Expense;
}

export const mockExpenses: Expense[] = [
  expense({
    id: "e1",
    groupId: "g1",
    category: "food",
    description: "Pizza night at Maison Thomas",
    totalAmount: 900,
    paidBy: "u1",
    participants: ["u1", "u2", "u3", "u4"],
    createdAt: "2026-08-28T20:10:00.000Z",
    source: "manual",
  }),
  expense({
    id: "e2",
    groupId: "g1",
    category: "transportation",
    description: "Uber back home",
    totalAmount: 240,
    paidBy: "u2",
    participants: ["u1", "u2", "u3"],
    createdAt: "2026-08-28T23:40:00.000Z",
    source: "manual",
  }),
  expense({
    id: "e3",
    groupId: "g1",
    category: "entertainment",
    description: "Cinema tickets — late show",
    totalAmount: 480,
    paidBy: "u4",
    participants: ["u1", "u2", "u3", "u4"],
    createdAt: "2026-09-02T21:00:00.000Z",
    source: "manual",
  }),
  expense({
    id: "e4",
    groupId: "g2",
    category: "accommodation",
    description: "Dahab guesthouse — 2 nights",
    totalAmount: 4500,
    paidBy: "u1",
    participants: ["u1", "u2", "u3", "u4", "u5"],
    createdAt: "2026-08-15T14:00:00.000Z",
    source: "manual",
  }),
  expense({
    id: "e5",
    groupId: "g2",
    category: "transportation",
    description: "Petrol + Sokhna toll",
    totalAmount: 1750,
    paidBy: "u3",
    participants: ["u1", "u2", "u3", "u4", "u5"],
    createdAt: "2026-08-15T08:20:00.000Z",
    source: "manual",
  }),
  expense({
    id: "e6",
    groupId: "g2",
    category: "food",
    description: "Seafood dinner on the beach",
    totalAmount: 2100,
    paidBy: "u2",
    participants: ["u1", "u2", "u3", "u4", "u5"],
    splitType: "custom",
    shares: { u1: 500, u2: 500, u3: 400, u4: 350, u5: 350 },
    createdAt: "2026-08-16T19:30:00.000Z",
    source: "receipt",
  }),
  expense({
    id: "e7",
    groupId: "g3",
    category: "utilities",
    description: "Electricity — August",
    totalAmount: 640,
    paidBy: "u5",
    participants: ["u1", "u5"],
    createdAt: "2026-09-01T10:00:00.000Z",
    source: "manual",
  }),
];

export const mockSettlements: Settlement[] = [
  {
    id: "s1",
    groupId: "g2",
    fromUser: "u4",
    toUser: "u1",
    amount: 300,
    status: "paid",
    paidAt: "2026-08-20T12:00:00.000Z",
  },
];

/** Mocked "OCR" output used by the receipt scan tab. */
export const mockReceiptItems = [
  { name: "Margherita pizza", quantity: 1, price: 320 },
  { name: "Beef burger", quantity: 2, price: 210 },
  { name: "Caesar salad", quantity: 1, price: 145 },
  { name: "Fresh orange juice", quantity: 3, price: 55 },
  { name: "Service & tax", quantity: 1, price: 98 },
];
