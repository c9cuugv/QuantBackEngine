# E2E Triage Report — 2026-05-23 04:18 MST

**Status:** RESOLVED — 13/13 passing

---

## Summary

| Suite | Before | After |
|-------|--------|-------|
| api/endpoints.spec.ts | 8 passed | 8 passed |
| ui/dashboard.spec.ts | 5 failed | 5 passed |
| Backend Maven (91 tests) | — | BUILD SUCCESS |

---

## Root Causes Found

### Bug 1 — Port 3000 Conflict (Environment)

**File:** `frontend/playwright.config.ts`

Port 3000 was occupied by unrelated `outbound-engine-frontend-1` Docker container
(`0.0.0.0:3000->80/tcp`) and a Vite dev server (`/outbound-engine/frontend`). Playwright
was hitting the wrong app ("OutboundEngine"), causing all UI tests to fail immediately.

**Fix:** Changed default dev server port to 3001 via `UI_PORT` env var and added a
`webServer` block so Playwright auto-starts the Next.js server on the correct port.

---

### Bug 2 — Wrong Selector: symbol is `<select>`, not `<input>`

**File:** `frontend/tests/e2e/ui/dashboard.spec.ts:15`

Test used `input[value="AAPL"]` but `page.tsx:226` renders symbol as a `<select>`.

**Fix:** Changed to `page.locator('select').first()` with `toHaveValue('AAPL')`.

---

### Bug 3 — Wrong Button Text

**File:** `frontend/tests/e2e/ui/dashboard.spec.ts:26`

Test searched for `button:has-text("Run")`, `button:has-text("Backtest")`,
`button:has-text("Execute")`. Actual button text (`page.tsx:327`) is `"Run Backtest"`.

**Fix:** Changed to `button:has-text("Run Backtest")`.

---

## GitHub Issue (gh auth not available — file as manual follow-up)

**Title:** `test(e2e): port 3000 conflict blocks UI tests when outbound-engine is running`

**Body:**
```
## Problem
E2E UI tests hit `http://localhost:3000` (Playwright `baseURL`) and receive
HTML from `outbound-engine-frontend-1` Docker container instead of the
QuantBackEngine Next.js frontend. All 5 UI dashboard tests fail with wrong
page title ("OutboundEngine") or missing elements.

## Repro
1. Have outbound-engine Docker stack running (`docker compose up` in sibling project)
2. Run `npm run test:e2e` from `frontend/`
3. All `ui/dashboard.spec.ts` tests fail

## Fix Applied
- `playwright.config.ts`: default port changed to 3001 via `UI_PORT` env var;
  `webServer` block added to auto-start `next dev -p $UI_PORT`
- Tests updated with correct selectors

## Selector Bugs Also Fixed
- Symbol is `<select>` not `<input>` → `page.locator('select').first()`
- Button text is `"Run Backtest"` not `"Run"` → `button:has-text("Run Backtest")`
```

---

## Artifacts

- `frontend/playwright-report/` — HTML report
- `frontend/playwright-results.json` — machine-readable results
- `frontend/artifacts/dashboard-load.png` — UI screenshot
- `frontend/artifacts/dashboard-results.png` — post-backtest screenshot
