import { api, backendErrorMessage, type ParsedReceipt } from "@/lib/api";
import type { Expense, ExpenseDraft, Group, Settlement, User } from "@/types";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

const STORAGE_KEY = "splitease.state.v1";
const USER_KEY = "splitease.user.v1";

interface PersistedState {
  currentUserId: string | null;
}

const initialState: PersistedState = { currentUserId: null };

interface AppContextValue extends PersistedState {
  hydrated: boolean;
  loading: boolean;
  error: string | null;
  currentUser: User | null;
  users: User[];
  friendIds: string[];
  groups: Group[];
  expenses: Expense[];
  settlements: Settlement[];
  friends: User[];
  userById: (id: string) => User;
  signIn: (email: string, password: string) => Promise<boolean>;
  signUp: (name: string, email: string, password: string) => Promise<void>;
  signOut: () => void;
  addFriend: (user: User) => Promise<void>;
  createGroup: (input: { name: string; image: string; memberIds: string[] }) => Promise<Group>;
  addExpense: (groupId: string, draft: ExpenseDraft) => Promise<void>;
  markSettlementPaid: (input: {
    groupId: string;
    fromUser: string;
    toUser: string;
    amount: number;
  }) => Promise<void>;
  parseExpense: (groupId: string, text: string) => Promise<ExpenseDraft>;
  parseReceipt: (groupId: string, image: File) => Promise<ParsedReceipt>;
  exportGroup: (groupId: string) => Promise<Blob>;
  expensesOfGroup: (groupId: string) => Expense[];
  settlementsOfGroup: (groupId: string) => Settlement[];
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<PersistedState>(initialState);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [users, setUsers] = useState<User[]>([]);
  const [friendIds, setFriendIds] = useState<string[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [settlements, setSettlements] = useState<Settlement[]>([]);
  const [hydrated, setHydrated] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async (user: User) => {
    setLoading(true);
    setError(null);
    try {
      const [nextGroups, nextFriends] = await Promise.all([
        api.listGroups(user.id),
        api.listFriends(user.id),
      ]);
      const loaded = await Promise.all(nextGroups.map((group) => api.loadGroup(group.id)));
      setGroups(loaded.map((item) => item.group));
      setUsers([user, ...nextFriends, ...loaded.flatMap((item) => item.users)].filter((candidate, index, all) =>
        all.findIndex((other) => other.id === candidate.id) === index,
      ));
      setFriendIds(nextFriends.map((friend) => friend.id));
      setExpenses(loaded.flatMap((item) => item.expenses));
      setSettlements(loaded.flatMap((item) => item.settlements));
    } catch (cause) {
      setError(backendErrorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) setState({ ...initialState, ...(JSON.parse(raw) as PersistedState) });
      const storedUser = localStorage.getItem(USER_KEY);
      if (storedUser) setCurrentUser(JSON.parse(storedUser) as User);
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

  useEffect(() => {
    if (!hydrated || !state.currentUserId || !currentUser) return;
    void refresh(currentUser);
  }, [currentUser, hydrated, refresh, state.currentUserId]);

  const userById = useCallback(
    (id: string): User =>
      users.find((u) => u.id === id) ?? {
        id,
        name: "Unknown",
        email: "",
        avatar: "?",
      },
    [users],
  );

  const signIn = useCallback(async (email: string, password: string) => {
    const user = await api.signIn(email, password);
    setCurrentUser(user);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    setUsers((previous) => [user, ...previous.filter((candidate) => candidate.id !== user.id)]);
    setState({ currentUserId: user.id });
    await refresh(user);
    return true;
  }, [refresh]);

  const signUp = useCallback(async (name: string, email: string, password: string) => {
    const user = await api.signUp(name, email, password);
    setCurrentUser(user);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    setUsers([user]);
    setState({ currentUserId: user.id });
    await refresh(user);
  }, [refresh]);

  const signOut = useCallback(() => {
    setState({ currentUserId: null });
    setCurrentUser(null);
    localStorage.removeItem(USER_KEY);
    setUsers([]);
    setFriendIds([]);
    setGroups([]);
    setExpenses([]);
    setSettlements([]);
  }, []);

  const addFriend = useCallback(async (user: User) => {
    if (!currentUser) return;
    const friend = await api.addFriend(currentUser.id, user.email || user.name);
    setUsers((previous) => [...previous.filter((candidate) => candidate.id !== friend.id), friend]);
    setFriendIds((previous) => (previous.includes(friend.id) ? previous : [...previous, friend.id]));
  }, [currentUser]);

  const createGroup = useCallback<AppContextValue["createGroup"]>(async (input) => {
    if (!currentUser) throw new Error("You must be signed in.");
    const group = await api.createGroup(currentUser.id, input);
    setGroups((previous) => [group, ...previous]);
    return group;
  }, [currentUser]);

  const addExpense = useCallback(async (groupId: string, draft: ExpenseDraft) => {
    const expense = await api.createExpense(groupId, draft);
    setExpenses((previous) => [...previous, expense]);
  }, []);

  const markSettlementPaid = useCallback<AppContextValue["markSettlementPaid"]>(async (input) => {
    const pending = await api.createSettlement(input.groupId, input.fromUser, input.toUser, input.amount);
    const settlement = await api.markSettlementPaid(pending.id, currentUser?.id ?? "");
    setSettlements((previous) => [...previous.filter((item) => item.id !== settlement.id), settlement]);
  }, [currentUser]);

  const parseExpense = useCallback(async (groupId: string, text: string) => {
    return api.parseExpense(groupId, text, users);
  }, [users]);

  const parseReceipt = useCallback((groupId: string, image: File) => api.parseReceipt(groupId, image), []);
  const exportGroup = useCallback((groupId: string) => api.exportGroup(groupId), []);

  const value = useMemo<AppContextValue>(
    () => ({
      ...state,
      users,
      friendIds,
      groups,
      expenses,
      settlements,
      hydrated,
      loading,
      error,
      currentUser,
      friends: friendIds.map(userById),
      userById,
      signIn,
      signUp,
      signOut,
      addFriend,
      createGroup,
      addExpense,
      markSettlementPaid,
      parseExpense,
      parseReceipt,
      exportGroup,
      expensesOfGroup: (groupId: string) => expenses.filter((e) => e.groupId === groupId),
      settlementsOfGroup: (groupId: string) => settlements.filter((s) => s.groupId === groupId),
    }),
    [
      state,
      users,
      friendIds,
      groups,
      expenses,
      settlements,
      hydrated,
      loading,
      error,
      currentUser,
      userById,
      signIn,
      signUp,
      signOut,
      addFriend,
      createGroup,
      addExpense,
      markSettlementPaid,
      parseExpense,
      parseReceipt,
      exportGroup,
    ],
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
