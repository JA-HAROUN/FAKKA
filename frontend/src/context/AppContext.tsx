import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { Expense, ExpenseDraft, Group, Settlement, User } from "@/types";
import {
  CURRENT_USER_ID,
  mockExpenses,
  mockGroups,
  mockSettlements,
  mockUsers,
} from "@/utils/mockData";

const STORAGE_KEY = "splitease.state.v1";

interface PersistedState {
  currentUserId: string | null;
  users: User[];
  friendIds: string[];
  groups: Group[];
  expenses: Expense[];
  settlements: Settlement[];
}

const initialState: PersistedState = {
  currentUserId: null,
  users: mockUsers,
  friendIds: ["u2", "u3", "u4", "u5"],
  groups: mockGroups,
  expenses: mockExpenses,
  settlements: mockSettlements,
};

interface AppContextValue extends PersistedState {
  hydrated: boolean;
  currentUser: User | null;
  friends: User[];
  userById: (id: string) => User;
  signIn: (email: string) => boolean;
  signUp: (name: string, email: string) => void;
  signOut: () => void;
  addFriend: (user: User) => void;
  createGroup: (input: { name: string; image: string; memberIds: string[] }) => Group;
  addExpense: (groupId: string, draft: ExpenseDraft) => void;
  markSettlementPaid: (input: {
    groupId: string;
    fromUser: string;
    toUser: string;
    amount: number;
  }) => void;
  expensesOfGroup: (groupId: string) => Expense[];
  settlementsOfGroup: (groupId: string) => Settlement[];
}

const AppContext = createContext<AppContextValue | null>(null);

const uid = (prefix: string) => `${prefix}${Math.random().toString(36).slice(2, 9)}`;

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<PersistedState>(initialState);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) setState({ ...initialState, ...(JSON.parse(raw) as PersistedState) });
    } catch {
      /* ignore corrupt storage */
    }
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      /* storage full or unavailable */
    }
  }, [state, hydrated]);

  const userById = useCallback(
    (id: string): User =>
      state.users.find((u) => u.id === id) ?? {
        id,
        name: "Unknown",
        email: "",
        avatar: "❓",
      },
    [state.users],
  );

  const signIn = useCallback((email: string) => {
    let ok = false;
    setState((prev) => {
      const match = prev.users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase());
      const target = match ?? prev.users.find((u) => u.id === CURRENT_USER_ID)!;
      ok = true;
      return { ...prev, currentUserId: target.id };
    });
    return ok;
  }, []);

  const signUp = useCallback((name: string, email: string) => {
    setState((prev) => {
      const existing = prev.users.find((u) => u.email.toLowerCase() === email.trim().toLowerCase());
      if (existing) return { ...prev, currentUserId: existing.id };
      const user: User = {
        id: uid("u"),
        name: name.trim() || "New user",
        email: email.trim(),
        avatar: "🙂",
      };
      return { ...prev, users: [...prev.users, user], currentUserId: user.id };
    });
  }, []);

  const signOut = useCallback(() => setState((prev) => ({ ...prev, currentUserId: null })), []);

  const addFriend = useCallback((user: User) => {
    setState((prev) => {
      if (prev.friendIds.includes(user.id)) return prev;
      const users = prev.users.some((u) => u.id === user.id) ? prev.users : [...prev.users, user];
      return { ...prev, users, friendIds: [...prev.friendIds, user.id] };
    });
  }, []);

  const createGroup = useCallback<AppContextValue["createGroup"]>((input) => {
    const group: Group = {
      id: uid("g"),
      name: input.name,
      image: input.image,
      members: input.memberIds,
      createdAt: new Date().toISOString(),
    };
    setState((prev) => ({ ...prev, groups: [group, ...prev.groups] }));
    return group;
  }, []);

  const addExpense = useCallback((groupId: string, draft: ExpenseDraft) => {
    const expense: Expense = {
      id: uid("e"),
      groupId,
      createdAt: new Date().toISOString(),
      category: draft.category,
      description: draft.description,
      totalAmount: draft.totalAmount,
      paidBy: draft.paidBy,
      participants: draft.participants,
      splitType: draft.splitType,
      shares: draft.shares,
      source: draft.source,
      ...(draft.image !== undefined ? { image: draft.image } : {}),
      ...(draft.items !== undefined ? { items: draft.items } : {}),
    };
    setState((prev) => ({ ...prev, expenses: [...prev.expenses, expense] }));
  }, []);

  const markSettlementPaid = useCallback<AppContextValue["markSettlementPaid"]>((input) => {
    setState((prev) => ({
      ...prev,
      settlements: [
        ...prev.settlements,
        {
          id: uid("s"),
          groupId: input.groupId,
          fromUser: input.fromUser,
          toUser: input.toUser,
          amount: input.amount,
          status: "paid",
          paidAt: new Date().toISOString(),
        },
      ],
    }));
  }, []);

  const value = useMemo<AppContextValue>(
    () => ({
      ...state,
      hydrated,
      currentUser: state.currentUserId ? userById(state.currentUserId) : null,
      friends: state.friendIds.map(userById),
      userById,
      signIn,
      signUp,
      signOut,
      addFriend,
      createGroup,
      addExpense,
      markSettlementPaid,
      expensesOfGroup: (groupId: string) => state.expenses.filter((e) => e.groupId === groupId),
      settlementsOfGroup: (groupId: string) =>
        state.settlements.filter((s) => s.groupId === groupId),
    }),
    [
      state,
      hydrated,
      userById,
      signIn,
      signUp,
      signOut,
      addFriend,
      createGroup,
      addExpense,
      markSettlementPaid,
    ],
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
