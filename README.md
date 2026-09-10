# FAKKA

**FAKKA** is a group expense management application that lets users create groups with friends, record shared expenses, and automatically calculate who owes whom. It offers two optional AI-assisted expense entry methods — natural language and receipt OCR — layered on top of a fully manual core workflow.

> "Fakka" (فكة) is Egyptian Arabic for "change" / small cash — fitting for an app about splitting bills.

---

## 📸 Screenshot

![FAKKA dashboard](https://github.com/user-attachments/assets/12c66e3d-3cf6-4d6c-8068-eb34fc21908c)

---

## ✨ Features

- **Groups & Friends** — create groups, add friends, and track shared spending together.
- **Expense Tracking** — log expenses with category, description, payer, and participants.
- **Flexible Splitting** — split expenses equally or by custom, per-person amounts.
- **Automatic Balance Engine** — calculates each member's balance (paid − owed) per group.
- **Who Owes Whom** — simplifies group debts into the minimum number of settling transactions.
- **Settlements** — mark debts as paid and keep balances up to date.
- **AI-Assisted Entry (optional)**
  - **Natural-language input** — describe an expense in plain English/Arabic and have an LLM turn it into a structured expense for review.
  - **Receipt OCR** — upload a receipt photo and have items auto-extracted for review.
  - Every AI-assisted feature has a full manual fallback — AI failure never blocks the core workflow.
- **Reports** — export a group's expense history and balances (CSV, with PDF as a stretch goal).

See [`Requirements.md`](./Requirements.md) for the full functional requirements and MVP scope breakdown.

---

## 🏗️ Tech Stack

**Backend**
- Java + [Spring Boot](https://spring.io/projects/spring-boot)
- Maven (`pom.xml`, `mvnw`)
- Layered architecture: `controller` → `service` → `repository` → `entity`, with `dto`, `config`, and `exception` layers
- Unit/integration tests under `src/test/java`

**Frontend**
- [Vite](https://vitejs.dev/) + TypeScript
- [TanStack Router](https://tanstack.com/router) (file-based routes in `src/routes`)
- Component-based UI (`components/ui` + feature folders: `auth`, `dashboard`, `group`, `friends`, `expense-modal`, `common`)
- Tailwind CSS

---

## 📁 Project Structure

```
FAKKA/
├── README.md
├── Requirements.md
├── SKILLS.md
├── backend/                    # Spring Boot backend
│   ├── pom.xml
│   ├── mvnw
│   └── src/
│       ├── main/
│       │   ├── java/com/oae/fakka/
│       │   │   ├── FakkaApplication.java
│       │   │   ├── config/
│       │   │   ├── controller/
│       │   │   ├── dto/
│       │   │   ├── entity/
│       │   │   ├── exception/
│       │   │   ├── repository/
│       │   │   └── service/
│       │   └── resources/
│       │       ├── application.properties
│       │       └── application-dev.properties
│       └── test/java/com/oae/fakka/
│           ├── controller/
│           ├── dto/
│           ├── exception/
│           └── service/
│
└── frontend/                  # Vite + TypeScript frontend
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── components.json
    ├── public/
    └── src/
        ├── components/
        │   ├── auth/
        │   ├── common/
        │   ├── dashboard/
        │   ├── expense-modal/
        │   ├── friends/
        │   ├── group/
        │   └── ui/
        ├── context/
        ├── hooks/
        ├── lib/
        ├── routes/
        │   ├── __root.tsx
        │   ├── index.tsx
        │   ├── dashboard.tsx
        │   ├── friends.tsx
        │   ├── group.$groupId.tsx
        │   └── style-preview.tsx
        ├── services/
        ├── types/
        ├── utils/
        ├── router.tsx
        ├── routeTree.gen.ts
        ├── server.ts
        ├── start.ts
        └── styles.css
```

---

## 🔗 Live Demo

Try FAKKA here: **[fakka.app](https://fakka.app)** *(replace with your actual deployed URL)*

---

## 🚀 Getting Started

### Prerequisites

- Java 17+ and Maven (or use the included `mvnw` wrapper)
- Node.js 18+ and a package manager (npm/pnpm/yarn)
- A database configured in `backend/src/main/resources/application-dev.properties`

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

The API will start on the port configured in `application.properties` (default `8080` unless overridden).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The dev server will print a local URL (default Vite port `5173`).

---

## 🧠 AI Features & Fallbacks

Both AI-assisted features are optional accelerators, not separate workflows:

| Feature | How it works | Fallback |
|---|---|---|
| Natural-language entry | User's text is sent to an LLM, which returns structured JSON that pre-fills the standard expense form | Manual expense form is always available |
| Receipt OCR | Receipt image is sent to an OCR service, which extracts line items | Manual item entry is always available |

In both cases, AI/OCR output is **never saved automatically** — the user must review, edit, and confirm before it becomes a real expense.

---

## 📊 MVP Scope

Prioritized as:

- 🔴 **Must have** — auth, groups, core expense creation/splitting, balance & debt-simplification engine, natural-language AI entry with manual fallback
- 🟠 **Should have** — receipt OCR, manual item entry, settlement status
- 🟢 **Stretch** — item-level participant assignment, structured OCR item extraction, expense images, PDF export, spending analytics

Full details in [`Requirements.md`](./Requirements.md).

---

## 📄 License

TBD.
