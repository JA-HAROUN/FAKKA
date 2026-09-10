# Split Smart

Build the frontend for "SplitEase" — a group expense management web app (like Splitwise). This is a hackathon MVP frontend only, so use realistic mock data / local state (no real backend needed yet, but structure the code so an API can be plugged in later). Use React, Tailwind, and shadcn/ui components. Clean, modern, mobile-first responsive design with a friendly fintech feel (soft cards, rounded corners, subtle shadows, clear positive/negative color coding).

## Core concept

Users sign up, join groups with friends, log shared expenses, and the app automatically calculates who owes whom. Every AI-assisted feature must have a manual fallback that's equally accessible.

## Pages / Flow

**1. Auth (Sign Up / Sign In)**

- Simple forms: name, email, password for sign up; email, password for sign in. No token auth needed, just mock/local state login.

- On success, redirect to Dashboard.

**2. Dashboard**

- Grid of "Group" cards. Each card shows: group image/emoji, group name, member count, and the current user's balance in that group with color coding:

  - 🟢 green "+350 EGP — You are owed"

  - 🔴 red "−250 EGP — You owe"

  - ⚪ gray "0 EGP — Settled"

- "Create Group" button (opens a modal/form: group name, image/emoji picker, select friends to add, creation date auto-set).

- A "Friends" tab/section: list of friends (name + avatar), "Add Friend" button (search by email/username, mock results), friends are selectable when creating a group.

**3. Group Dashboard** (clicking a group card)

- Header: group name, image, member avatars, total group expenses.

- Individual member balances list (who's owed / who owes, color-coded).

- "Who Owes Whom" settlement panel — simplified debts, e.g. "Ahmed → John: 200 EGP" with a status badge (Pending / Paid) and a "Mark as Paid" button.

- List of expenses (chronological), each showing category icon, description, amount, payer, date.

- "Add Expense" button (prominent, opens the expense creation flow).

- "Export Report" button (dropdown: PDF or CSV — can just be a UI stub/mock download for now).

**4. Add Expense flow**

Present as a modal or dedicated screen with three entry-method tabs, all leading to the same review form:

- **Manual Entry tab**: category dropdown (Food, Transportation, Entertainment, Shopping, Accommodation, Utilities, Other — with icons), description text field, optional image upload, total amount, "who paid" (single-select radio list of group members), "who it's for" (multi-select checkboxes), split method toggle:

  - Equal split — auto-calculates and displays each participant's share.

  - Custom split — input fields per participant; live-validate that the sum equals the total (block save + show inline error if it doesn't match).

- **Natural Language tab**: a text box ("e.g. John paid 900 EGP for dinner. John and Ahmed shared the pizza and Mohamed had the burger.") + "Generate" button. Mock the AI response with a short loading state, then auto-populate the SAME manual entry form fields shown above so the user can review/edit/confirm/cancel before saving. Include a visible fallback note: "AI unavailable? Switch to Manual Entry."

- **Receipt Scan tab**: image upload/camera input for a receipt. Mock OCR with a loading state, then populate an itemized list (item name, quantity, price) that flows into a "Purchased Items" table where each item can be assigned to one or more participants (checkboxes per item). Include manual "Add Item" row entry as an always-available alternative, and a fallback note if OCR fails.

- All three tabs funnel into one final "Review Expense" step before "Save Expense."

**5. Settlement / Report views**

- Settlement list per group with status badges (Pending/Paid) and mark-as-paid action.

- Report export view/modal: preview of what will be exported (group name, members, expenses w/ dates & categories & payers & shares, totals, balances, settlements) with PDF/CSV export buttons (CSV can be a real client-side CSV download of the mock data; PDF can be a stubbed button).

## Data model to mock (use realistic sample data: a "Dinner" group with 4-5 friends, an Egypt trip group, EGP currency)

- User: id, name, email, avatar

- Group: id, name, image, members[], createdAt

- Expense: id, groupId, category, description, image?, totalAmount, paidBy, participants[], splitType (equal/custom), shares{userId: amount}, items[] (optional), createdAt

- Settlement: id, groupId, fromUser, toUser, amount, status (pending/paid)

## Design notes

- Use clear iconography per category (utensils, car, popcorn, shopping bag, bed, plug, etc.)

- Balance colors: green (positive), red (negative), gray (zero) used consistently everywhere (cards, lists, settlement panel).

- Empty states for no groups / no friends / no expenses yet.

- Keep the AI and OCR entry methods visually distinct (e.g. a small "AI" badge) but styled as first-class, not gimmicky, tabs — never hide the manual option behind them.

Build this as a fully clickable prototype with mock data and local state so all flows (create group, add friend, add expense via all 3 methods, mark settlement paid, export CSV) work end-to-end without a real backend.

File structure should look something like this

```text

src/

├── components/

│   ├── auth/

│   │   ├── LoginForm.tsx

│   │   └── RegisterForm.tsx

│   ├── dashboard/

│   │   ├── GroupCard.tsx

│   │   └── BalanceSummary.tsx

│   ├── group/

│   │   ├── GroupHeader.tsx

│   │   ├── ExpenseList.tsx

│   │   ├── ExpenseItem.tsx

│   │   ├── SettlementList.tsx

│   │   └── ExportButton.tsx

│   ├── expense-modal/

│   │   ├── AddExpenseModal.tsx

│   │   ├── ManualExpenseForm.tsx

│   │   ├── AiExpenseInput.tsx

│   │   └── ReceiptOcrUpload.tsx

│   ├── friends/

│   │   └── FriendList.tsx

│   └── ui/ (Shadcn UI components: Button, Dialog, Input, Select, Badge, Card, Tabs)

├── context/

│   └── AppContext.tsx (Global state for current user, groups, expenses, and friends)

├── types/

│   └── index.ts (Data models for User, Group, Expense, Split, Settlement)

├── utils/

│   ├── calculations.ts (Debt simplification, balance formulas, split logic)

│   ├── mockData.ts (Initial populated mock groups, friends, and expenses)

│   └── exportCsv.ts (CSV generation utility)

├── pages/

│   ├── AuthPage.tsx

│   ├── DashboardPage.tsx

│   ├── GroupPage.tsx

│   └── FriendsPage.tsx

├── App.tsx

└── main.tsx

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/50e4941b-1f0c-4f45-8750-0275ed945370).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
