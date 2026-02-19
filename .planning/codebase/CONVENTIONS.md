# Coding Conventions

**Analysis Date:** 2024-12-25

## Naming Patterns

**Files:**
- **Backend (Java):** `PascalCase.java` for classes (e.g., `BacktestService.java`).
- **Frontend (Next.js/TS):** 
  - Components: `PascalCase.tsx` (e.g., `TradingChart.tsx`).
  - Next.js App Router files: `page.tsx`, `layout.tsx`, `route.ts`.
  - Config: `lowercase.json` (e.g., `tsconfig.json`).

**Functions/Methods:**
- **Backend:** `camelCase` for methods (e.g., `runBacktest`).
- **Frontend:** `camelCase` for functions and React hooks (e.g., `fetchStrategies`, `useCallback`).

**Variables:**
- **Backend:** `camelCase` for local variables and fields (e.g., `initialCapital`).
- **Frontend:** `camelCase` for state variables and props (e.g., `selectedStrategy`, `setLoading`).

**Types/Interfaces:**
- **Backend:** `PascalCase` for DTOs and Domains (e.g., `BacktestRequest`, `BacktestRun`).
- **Frontend:** `PascalCase` for Interfaces (e.g., `BacktestResult`, `Strategy`).

## Code Style

**Formatting:**
- **Backend:** Standard Java formatting with 4-space indentation. Uses Lombok to reduce boilerplate.
- **Frontend:** Standard TypeScript/React formatting (likely Prettier-based defaults from Next.js). Uses 4-space indentation as seen in `app/page.tsx`.

**Linting:**
- **Backend:** Not explicitly configured beyond default IDE settings.
- **Frontend:** Uses `next lint` (configured in `frontend/package.json`).

## Import Organization

**Backend:**
- Packages are grouped by: standard library (`java.*`), third-party (`org.ta4j.*`, `lombok.*`), Spring framework (`org.springframework.*`), and then project-specific imports.

**Frontend:**
- Order:
  1. React/Next.js core (`useState`, `useEffect`)
  2. Third-party libraries (`lucide-react`, `lightweight-charts`)
  3. Local components using `@/` alias (e.g., `@/components/TradingChart`)

**Path Aliases:**
- **Frontend:** `@/*` points to `./*` as defined in `frontend/tsconfig.json`.

## Error Handling

**Patterns:**
- **Backend:**
  - Uses a central `GlobalExceptionHandler.java` with `@RestControllerAdvice`.
  - Maps specific exceptions (`IllegalArgumentException`, `IllegalStateException`) to appropriate HTTP status codes (400, 422).
  - Returns structured JSON error responses with `timestamp`, `status`, `error`, and `message`.
- **Frontend:**
  - Standard `try/catch` blocks around `fetch` operations.
  - Checks `res.ok` and parses error messages from response body when possible.
  - Stores error state to display to the user in the UI.

## Logging

**Framework:** SLF4J with Logback (via Spring Boot).

**Patterns:**
- Uses `@Slf4j` Lombok annotation on classes.
- Log levels used: `info` for major lifecycle events and completions, `warn` for handled bad requests, `error` for unexpected failures.

## Comments

**When to Comment:**
- Classes and public methods in the backend often have Javadoc-style headers.
- Complex logic (like metric calculation in `BacktestService.java`) has inline comments.

**JSDoc/TSDoc:**
- Observed on backend classes: `/** Core backtesting service. ... */`.

## Function Design

**Size:** Most functions are focused, but core logic like `runBacktest` can be long (around 100 lines) due to data mapping.

**Parameters:** 
- **Backend:** Uses Request DTOs (`BacktestRequest`) for complex inputs.
- **Frontend:** Uses props objects for components.

**Return Values:** 
- **Backend:** Uses Response DTOs (`BacktestResponse`) or `ResponseEntity`.
- **Frontend:** Standard React state updates or `Promise<void>` for async operations.

## Module Design

**Exports:** 
- **Backend:** Public classes within packages.
- **Frontend:** Default exports for pages/components (`export default function Dashboard()`).

**Barrel Files:** Not observed in this codebase.

---

*Convention analysis: 2024-12-25*
