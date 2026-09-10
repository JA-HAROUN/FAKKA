import type { CategoryId, ExpenseDraft, User } from "@/types";
import { equalShares, round2 } from "./calculations";

const CATEGORY_HINTS: Record<CategoryId, string[]> = {
  food: ["dinner", "lunch", "breakfast", "pizza", "burger", "food", "restaurant", "coffee"],
  transportation: ["uber", "taxi", "petrol", "fuel", "train", "bus", "toll", "flight"],
  entertainment: ["cinema", "movie", "concert", "game", "tickets", "party"],
  shopping: ["shopping", "clothes", "mall", "groceries", "market"],
  accommodation: ["hotel", "airbnb", "guesthouse", "hostel", "stay", "nights"],
  utilities: ["electricity", "water", "internet", "wifi", "gas bill", "bill"],
  other: [],
};

/**
 * Mocked "AI" parser. Swap the body for a real API call later — the return
 * shape is the same ExpenseDraft the manual form edits.
 */
export function parseExpenseText(text: string, members: User[], fallbackPayer: string): ExpenseDraft {
  const lower = text.toLowerCase();

  const amountMatch = lower.match(/(\d[\d,]*(?:\.\d+)?)\s*(?:egp|le|pounds?)?/);
  const totalAmount = amountMatch?.[1] ? round2(Number(amountMatch[1].replace(/,/g, ""))) : 0;

  const firstName = (name: string) => (name.split(" ")[0] ?? name).toLowerCase();

  const mentioned = members.filter((m) => lower.includes(firstName(m.name)));

  const payer =
    members.find((m) => new RegExp(`${firstName(m.name)}\\s+(paid|covered|put)`).test(lower))?.id ??
    fallbackPayer;

  const participants = (mentioned.length > 0 ? mentioned : members).map((m) => m.id);

  let category: CategoryId = "other";
  for (const [id, hints] of Object.entries(CATEGORY_HINTS) as [CategoryId, string[]][]) {
    if (hints.some((h) => lower.includes(h))) {
      category = id;
      break;
    }
  }

  const description =
    (text.trim().split(/[.\n]/)[0] ?? "").slice(0, 80) || "Expense from natural language";

  return {
    category,
    description,
    totalAmount,
    paidBy: payer,
    participants,
    splitType: "equal",
    shares: equalShares(totalAmount, participants),
    source: "ai",
  };
}
