import type { CategoryId, Expense, ExpenseDraft, Group, Settlement, User } from "@/types";

const API_BASE_URL = (import.meta.env["VITE_API_URL"] ?? "http://localhost:8080").replace(/\/$/, "");

interface BackendUser {
  id: number;
  name: string;
  email: string;
  profileImageUrl: string | null;
}

interface BackendGroupCard {
  groupId: number;
  name: string;
  imageUrl: string | null;
  memberCount: number;
  userBalance: number;
}

interface BackendGroup {
  id: number;
  name: string;
  imageUrl: string | null;
  createdBy: number;
  createdAt: string;
  memberCount: number;
}

interface BackendMember {
  userId: number;
  name: string;
  profileImageUrl: string | null;
}

interface BackendShare {
  userId: number;
  amount: number;
}

interface BackendExpense {
  id: number;
  groupId: number;
  category: string;
  description: string;
  totalAmount: number;
  imageUrl: string | null;
  paidByUserId: number;
  createdAt: string;
  participants: BackendShare[];
}

interface BackendSettlement {
  id: number;
  groupId: number;
  fromUserId: number;
  toUserId: number;
  amount: number;
  status: "PENDING" | "PAID";
  createdAt: string;
  paidAt: string | null;
}

interface BackendSuggestedSettlement {
  fromUserId: number;
  toUserId: number;
  amount: number;
}

interface BackendDashboard {
  group: BackendGroup;
  members: Array<BackendMember & { net: number }>;
  totalGroupExpenses: number;
  expenses: { content: BackendExpense[] };
  suggestedSettlements: BackendSuggestedSettlement[];
  pendingSettlements: BackendSettlement[];
}

interface BackendParsedExpense {
  description: string;
  totalAmount: number;
  suggestedCategory: string | null;
  paidBy: BackendMember;
  participants: BackendMember[];
  splitType: "EQUAL" | "CUSTOM";
  unresolvedNames: string[];
}

interface BackendReceiptItem {
  name: string;
  quantity: number;
  unitPricePiastres: number;
}

export interface ParsedReceipt {
  items: BackendReceiptItem[];
  suggestedTotalPiastres: number;
}

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      ...(init?.body instanceof FormData ? {} : { "Content-Type": "application/json" }),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // Keep the status-based message when the server did not return JSON.
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

function toUser(user: BackendUser | BackendMember): User {
  return {
    id: String("id" in user ? user.id : user.userId),
    name: user.name,
    email: "email" in user ? user.email : "",
    avatar: user.profileImageUrl ?? user.name,
  };
}

function toCategory(category: string): CategoryId {
  return category.toLowerCase() as CategoryId;
}

function toGroup(group: BackendGroup | BackendGroupCard): Group {
  const isCard = "groupId" in group;
  return {
    id: String(isCard ? group.groupId : group.id),
    name: group.name,
    image: group.imageUrl ?? "◉",
    members: [],
    createdAt: "createdAt" in group ? group.createdAt : new Date().toISOString(),
  };
}

function toExpense(expense: BackendExpense): Expense {
  return {
    id: String(expense.id),
    groupId: String(expense.groupId),
    category: toCategory(expense.category),
    description: expense.description,
    totalAmount: expense.totalAmount / 100,
    paidBy: String(expense.paidByUserId),
    participants: expense.participants.map((share) => String(share.userId)),
    splitType: "custom",
    shares: Object.fromEntries(
      expense.participants.map((share) => [String(share.userId), share.amount / 100]),
    ),
    createdAt: expense.createdAt,
    ...(expense.imageUrl ? { image: expense.imageUrl } : {}),
  };
}

function toSettlement(settlement: BackendSettlement): Settlement {
  return {
    id: String(settlement.id),
    groupId: String(settlement.groupId),
    fromUser: String(settlement.fromUserId),
    toUser: String(settlement.toUserId),
    amount: settlement.amount / 100,
    status: settlement.status.toLowerCase() as Settlement["status"],
    ...(settlement.paidAt ? { paidAt: settlement.paidAt } : {}),
  };
}

export const api = {
  signIn(email: string, password: string) {
    return request<BackendUser>("/api/auth/signin", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }).then(toUser);
  },

  signUp(name: string, email: string, password: string) {
    return request<BackendUser>("/api/auth/signup", {
      method: "POST",
      body: JSON.stringify({ name, email, password }),
    }).then(toUser);
  },

  listGroups(userId: string) {
    return request<BackendGroupCard[]>(`/api/users/${userId}/groups`).then((groups) =>
      groups.map(toGroup),
    );
  },

  loadGroup(groupId: string) {
    return Promise.all([
      request<BackendDashboard>(`/api/groups/${groupId}/dashboard?size=100`),
      request<BackendMember[]>(`/api/groups/${groupId}/members`),
    ]).then(([dashboard, members]) => ({
      group: {
        ...toGroup(dashboard.group),
        members: members.map((member) => String(member.userId)),
      },
      users: members.map(toUser),
      expenses: dashboard.expenses.content.map(toExpense),
      settlements: dashboard.pendingSettlements.map(toSettlement),
      suggestedSettlements: dashboard.suggestedSettlements,
    }));
  },

  listFriends(userId: string) {
    return request<BackendMember[]>(`/api/friends?userId=${userId}`).then((friends) =>
      friends.map(toUser),
    );
  },

  addFriend(userId: string, query: string) {
    const isEmail = query.includes("@");
    return request<BackendMember>("/api/friends", {
      method: "POST",
      body: JSON.stringify({ userId: Number(userId), ...(isEmail ? { email: query } : { username: query }) }),
    }).then(toUser);
  },

  createGroup(userId: string, input: { name: string; image: string; memberIds: string[] }) {
    return request<BackendGroup>("/api/groups", {
      method: "POST",
      body: JSON.stringify({
        createdBy: Number(userId),
        name: input.name,
        imageUrl: input.image.startsWith("http") ? input.image : null,
        memberUserIds: input.memberIds.filter((id) => id !== userId).map(Number),
      }),
    }).then((group) => ({ ...toGroup(group), members: [userId, ...input.memberIds.filter((id) => id !== userId)] }));
  },

  createExpense(groupId: string, draft: ExpenseDraft) {
    const customShares = Object.fromEntries(
      Object.entries(draft.shares).map(([id, amount]) => [id, Math.round(amount * 100)]),
    );
    return request<BackendExpense>(`/api/groups/${groupId}/expenses`, {
      method: "POST",
      body: JSON.stringify({
        paidByUserId: Number(draft.paidBy),
        category: draft.category.toUpperCase(),
        description: draft.description,
        totalAmount: Math.round(draft.totalAmount * 100),
        participantUserIds: draft.participants.map(Number),
        splitType: draft.splitType.toUpperCase(),
        ...(draft.splitType === "custom" ? { customShares } : {}),
      }),
    }).then(toExpense);
  },

  createSettlement(groupId: string, fromUserId: string, toUserId: string, amount: number) {
    return request<BackendSettlement>(`/api/groups/${groupId}/settlements`, {
      method: "POST",
      body: JSON.stringify({
        fromUserId: Number(fromUserId),
        toUserId: Number(toUserId),
        amount: Math.round(amount * 100),
      }),
    }).then(toSettlement);
  },

  parseExpense(groupId: string, text: string, members: User[]) {
    return request<BackendParsedExpense>(`/api/groups/${groupId}/expenses/parse-nl`, {
      method: "POST",
      body: JSON.stringify({ text }),
    }).then((parsed) => {
      const participants = parsed.participants.map((member) => String(member.userId));
      const totalAmount = parsed.totalAmount / 100;
      return {
        category: parsed.suggestedCategory ? toCategory(parsed.suggestedCategory) : "other",
        description: parsed.description,
        totalAmount,
        paidBy: String(parsed.paidBy.userId),
        participants,
        splitType: parsed.splitType.toLowerCase() as ExpenseDraft["splitType"],
        shares: Object.fromEntries(
          participants.map((id) => [id, totalAmount / Math.max(participants.length, 1)]),
        ),
        source: "ai" as const,
        members,
        unresolvedNames: parsed.unresolvedNames,
      };
    });
  },

  parseReceipt(groupId: string, image: File) {
    const form = new FormData();
    form.append("image", image);
    return request<ParsedReceipt>(`/api/groups/${groupId}/expenses/parse-receipt`, {
      method: "POST",
      body: form,
    });
  },

  markSettlementPaid(settlementId: string, userId: string) {
    return request<BackendSettlement>(`/api/settlements/${settlementId}/pay`, {
      method: "PATCH",
      body: JSON.stringify({ userId: Number(userId) }),
    }).then(toSettlement);
  },

  exportGroup(groupId: string) {
    return fetch(`${API_BASE_URL}/api/groups/${groupId}/export?format=csv`).then(async (response) => {
      if (!response.ok) throw new ApiError(response.status, "Could not export this report");
      return response.blob();
    });
  },
};

export function backendErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Something went wrong. Please try again.";
}
