# Functional Requirements Document
## Group Expense Management Application

---

## 1. Product Overview

A group expense management platform enabling users to:
- Create groups with friends
- Record shared expenses
- Automatically calculate debts/credits
- Determine who owes whom

**AI-Assisted Entry Methods (Optional):**
- Natural-language expense entry (LLM)
- Receipt image processing (OCR)

> **Critical Rule:** Every AI operation must have a manual alternative. Users must complete the full workflow without external AI services.

---

## 2. User Flow

```
Sign Up / Sign In
       ↓
Personal Dashboard
       ↓
Groups/Rooms (Group Cards + Financial Status)
       ↓
Select a Group → Group Dashboard → Add Expense
       ↓
┌──────────┬──────────────┬─────────────┐
│  Manual  │  Natural     │  Receipt    │
│  Entry   │  Language    │  OCR        │
└──────────┴──────────────┴─────────────┘
       ↓
Review Expense → Save → Automatic Calculation
       ↓
Group Balances → Who Owes Whom → Export Report
```

---

## 3. Authentication

| ID | Requirement |
|----|-------------|
| FR-1 | **Sign Up:** Create account with Name, Email, Password. Validate info; prevent duplicate emails. **No token auth needed.** |
| FR-2 | **Sign In:** Email + Password. Redirect to dashboard on success. |
| FR-3 | **User Profile:** User ID, Name, Email, Profile image (optional). |

---

## 4. Main Dashboard

| ID | Requirement |
|----|-------------|
| FR-4 | **Group/Room List:** Display all user's groups as cards. Each card shows: Group name, image, member count, user's financial balance. |
| FR-5 | **Group Financial Status:** Visual indicator — 🟢 Positive (+EGP, owed), 🔴 Negative (−EGP, owes), ⚪ Zero (settled). |

**Example Card:**
```
┌───────────────────────────┐
│        🍕 Dinner          │
│  5 members                │
│  You are owed +350 EGP    │
└───────────────────────────┘
```

---

## 5. Group Creation

| ID | Requirement |
|----|-------------|
| FR-6 | **Create Group:** Form with Group name, image, members/friends. Creation date auto-generated. |
| FR-7 | **Add Members:** Creator selects friends. Creator automatically becomes a member. |

---

## 6. Friends

| ID | Requirement |
|----|-------------|
| FR-8 | **Friends Tab:** Dedicated section to view existing friends. |
| FR-9 | **Add Friend:** Add registered users via email or username search. |
| FR-10 | **Friend List:** Display Name + Profile image. Friends selectable when creating groups. |

---

## 7. Group Dashboard

| ID | Requirement |
|----|-------------|
| FR-11 | **View Group:** Shows group name, image, members, total expenses, individual balances, expense list, add-expense option, settlement info, export option. |

---

## 8. Expense Creation

| ID | Requirement |
|----|-------------|
| FR-12 | **Add Expense:** Fields — Category, Description, Optional image, Total amount, Payer, Participants, Split method. |
| FR-13 | **Category:** Predefined list — Food, Transportation, Entertainment, Shopping, Accommodation, Utilities, Other. |
| FR-14 | **Description:** Text input (e.g., "Dinner at Pizza Hut"). |
| FR-15 | **Image:** Optional attachment (receipt, photo, supporting image). |

---

## 9. Expense Splitting

| ID | Requirement |
|----|-------------|
| FR-16 | **Select Payer:** Exactly one member designated as payer. |
| FR-17 | **Select Participants:** Multiple members selectable. |
| FR-18 | **Equal Split:** System auto-calculates equal shares (e.g., 900 EGP ÷ 3 = 300 each). |
| FR-19 | **Unequal/Custom Split:** User specifies each amount. Validate: Sum of shares = Total. Reject save if mismatch. |

---

## 10. Natural-Language Expense Entry

| ID | Requirement |
|----|-------------|
| FR-20 | **Natural-Language Input:** Optional AI method. Example: "John paid 900 EGP for dinner. John and Ahmed shared pizza, Mohamed had burger." |
| FR-21 | **LLM Processing:** Send description to external LLM → returns structured JSON (description, totalAmount, paidBy, participants, splitType). |
| FR-22 | **AI Result Review:** Never auto-saved. Populate standard form. User can Review, Modify, Confirm, or Cancel. |
| FR-23 | **Manual Fallback:** Manual form always available. If LLM unavailable/limited/fails/incomplete → user enters manually. **AI accelerates existing workflow; does not create separate workflow.** |

**Example JSON:**
```json
{
  "description": "Dinner",
  "totalAmount": 900,
  "paidBy": "John",
  "participants": ["John", "Ahmed", "Mohamed"],
  "splitType": "equal"
}
```

---

## 11. Receipt OCR

| ID | Requirement |
|----|-------------|
| FR-24 | **Receipt Upload:** Optional upload/photograph of receipt. |
| FR-25 | **OCR Processing:** Send image to external OCR → extract text (item names, quantities, prices, total). |
| FR-26 | **Automatic Item Entry:** Populate purchased-items interface. User reviews/edits before saving. |
| FR-27 | **Manual Item Entry:** Always available. Example: Pizza ×1 = 350 EGP; Burger ×1 = 200 EGP; Coke ×2 = 100 EGP. |
| FR-28 | **OCR Failure Fallback:** Continue with manual item entry. |

---

## 12. Purchased Items

| ID | Requirement |
|----|-------------|
| FR-29 | **Item Management:** Each item supports — Name, Quantity, Unit/total price, Participants responsible. |
| FR-30 | **Item Assignment:** Assign items to one or more members. **Can be implemented after core splitting if time limited.** |

**Example:**
```
Pizza — 400 EGP    ☑ John  ☑ Ahmed
Burger — 250 EGP   ☑ Mohamed
```

---

## 13. Automatic Balance Calculation

| ID | Requirement |
|----|-------------|
| FR-31 | **Calculate Individual Balance:** Considers amount paid, amount owed, all confirmed expenses, confirmed settlements. **Formula: Balance = Total Paid − Total Owed.** |

**Example:** John — Paid: 1,000 EGP, Owed: 700 EGP → Balance: +300 EGP

---

## 14. Who Owes Whom

| ID | Requirement |
|----|-------------|
| FR-32 | **Generate Debts:** Calculate outstanding debts (e.g., Ahmed owes John 200 EGP; Mohamed owes John 150 EGP). |
| FR-33 | **Simplify Debts:** Minimize transactions. Show recommended settlement transactions. |

**Example Settlement:**
```
💸 Ahmed → John: 200 EGP
💸 Mohamed → John: 150 EGP
```

---

## 15. Settlement Status

| ID | Requirement |
|----|-------------|
| FR-34 | **Statuses:** Minimum — Pending, Paid. |
| FR-35 | **Mark as Paid:** Relevant user marks settlement paid. Balance updates accordingly. |

---

## 16. Group Expense Report

| ID | Requirement |
|----|-------------|
| FR-36 | **Export Report:** Contains group name, members, outings/expenses, dates, categories, payers, individual shares, total expenses, balances, outstanding settlements. |
| FR-37 | **Format:** PDF or CSV. **Prioritize CSV** (simpler for hackathon). |

---

## 17. Core Business Rules

| ID | Rule |
|----|------|
| BR-1 | **Expense Validity:** Total expense = Sum of all participant shares |
| BR-2 | **Payer:** Exactly one payer per expense |
| BR-3 | **Participants:** At least one participant per expense |
| BR-4 | **Balance:** Balance = Amount Paid − Amount Owed |
| BR-5 | **Group Balance:** Sum of all member balances = zero (excluding rounding) |
| BR-6 | **AI Confirmation:** AI-generated expenses must be reviewed/confirmed before becoming official |
| BR-7 | **AI Independence:** AI/OCR failure must never prevent manual expense entry |

---

## 18. MVP Scope (4-Hour Hackathon)

### 🔴 Must Have
- **Authentication:** Sign up, Sign in
- **Groups:** Create group, Add friends/members, Group cards, Financial status
- **Expenses:** Create expense, Category, Description, Amount, Payer, Participants, Equal split, Custom split
- **Financial Engine:** Automatic balances, Who owes whom, Debt simplification, Positive/negative indicators
- **AI:** Natural-language entry, LLM → JSON → normal form, Manual fallback

### 🟠 Should Have
- Receipt image upload, OCR, Manual purchased-item entry, OCR → item entry, Settlement status

### 🟢 Stretch
- Item-level participant assignment, OCR → structured item extraction, Expense images, PDF export, Advanced spending analytics

---

## Recommended Architecture Note

**Consider separating "Outing" and "Expense" concepts:**

```
GROUP
  ├── Outing: "Dinner at Pizza Hut"
  │      ├── Expense: Pizza
  │      ├── Expense: Drinks
  │      └── Expense: Uber
  ├── Outing: "Alexandria Trip"
  │      ├── Expense: Hotel
  │      └── Expense: Food
  └── ...
```

> **Simplest implementation:** Treat an outing as a container for expenses; avoid overengineering.

**Core Architecture:**
```
Manual Entry → AI-assisted Entry → OCR-assisted Entry
                        ↓
              Same Expense Model
                        ↓
              Same Balance Engine
```

One reliable financial engine + AI features as the "wow" layer on top.
