# E2E Triage Report — 2026-06-01 04:10 MST

**Run type:** Automated scheduled triage loop
**Commit:** `90e0487`
**Duration:** ~18s
**Stack:** All 4 containers healthy (nginx:80, frontend:3000, backend:8080, db:5432)

---

## Baseline E2E: 17/17 PASS — no regressions

| Suite | Tests | Status |
|-------|-------|--------|
| `api/endpoints.spec.ts` | 12 | ✅ All pass |
| `ui/dashboard.spec.ts` | 5 | ✅ All pass |

**No failures detected.** GitHub issue filing and code fix steps skipped per triage protocol.

### Test Coverage Verified
- `GET /api/v1/backtest/strategies` → 200, non-empty array with `id/name/parameters`
- `GET /api/v1/backtest/strategies/{id}` → 200 detail + 404 for unknown id
- `GET /api/v1/market-data/symbols` + `sources` → 200 array
- `POST /api/v1/backtest/run` SMA_CROSSOVER → 200 with `metrics.totalReturn/sharpeRatio/maxDrawdown`
- `POST /api/v1/backtest/run` invalid payload → 400
- `GET /api/health` → 200 (health stub)
- `GET /api/v1/market-data/python/{symbol}` → 200 or 400 (bridge-dependent)
- `POST /api/v1/analytics/quantstats` → 200 with `success` field
- `GET /api/v1/data/symbols` → 200 list
- `POST /api/v1/data/upload` + `DELETE` lifecycle → success
- Dashboard loads with correct title, AAPL/SMA_CROSSOVER defaults, Run Backtest button
- Metrics cards render after backtest trigger

### Impact Analysis
No code changes this cycle → no downstream regression risk.

---

# E2E Triage Report — 2026-05-31 11:10 MST

**Run type:** Automated scheduled triage loop
**Commit:** `90e0487`
**Duration:** ~20 minutes

---

## Baseline E2E (pre-fix): 17/17 PASS

| Suite | Tests | Status |
|-------|-------|--------|
| `api/endpoints.spec.ts` | 12 | ✅ All pass |
| `ui/dashboard.spec.ts` | 5 | ✅ All pass |

---

## Infrastructure Bugs Found & Fixed

### BUG-1 — nginx health check failing (FailingStreak: 8614)

**Severity:** Medium — nginx functionally serving traffic; Docker reports unhealthy
**Root cause:** Health check `wget -qO /dev/null http://localhost/` proxies through Next.js
upstream. The probe intermittently returns 404 from within Docker's health daemon context
even though nginx routes `/` correctly for external requests. Health stub depended on
upstream availability — wrong design for a reverse-proxy health check.

**Fix:**
- `nginx/nginx.conf`: added `location /nginx-healthz { return 200 'ok\n'; }` — direct stub, no upstream
- `docker-compose.yml`: updated nginx healthcheck URL to `/nginx-healthz`
- Hot-reloaded nginx config; rebuilt and restarted container

**Verification:** FailingStreak reset to 0; last 3 Docker health checks all exit 0.

---

### BUG-2 — Backend returns HTTP 500 for all unmapped paths

**Severity:** High — incorrect HTTP semantics; floods logs with spurious ERROR entries
**Root cause:** Spring Boot 3.2 / Spring MVC 6 throws `NoResourceFoundException` for paths
with no registered handler. `GlobalExceptionHandler` had no specific handler for this type,
so the catch-all `@ExceptionHandler(Exception.class)` intercepted it and returned HTTP 500.
Every probe to `/api/health` or `/actuator/health` generated a spurious ERROR log.

**Stack trace:**
```
NoResourceFoundException: No static resource api/health.
  at ExceptionTranslationFilter.doFilter (spring-security-web-6.2.0.jar)
```

**Fix:**
1. `GlobalExceptionHandler.java`: added `@ExceptionHandler(NoResourceFoundException.class)` → 404
2. `HealthController.java`: new `GET /api/health` → `{"status":"UP","timestamp":"..."}`
3. `docker-compose.yml`: backend healthcheck updated to `http://localhost:8080/api/health`

**Verification:** `GET /api/health` → HTTP 200 `{"status":"UP"}`.

---

## Final E2E (post-fix): 17/17 PASS — no regressions

| Suite | Tests | Status |
|-------|-------|--------|
| `api/endpoints.spec.ts` | 12 | ✅ All pass |
| `ui/dashboard.spec.ts` | 5 | ✅ All pass |

---

## Files Modified

| File | Change |
|------|--------|
| `nginx/nginx.conf` | Added `/nginx-healthz` stub location |
| `docker-compose.yml` | Updated nginx + backend healthcheck URLs |
| `backend/.../exception/GlobalExceptionHandler.java` | Added `NoResourceFoundException` → 404 handler |
| `backend/.../controller/HealthController.java` | New — `GET /api/health` endpoint |

---

## Container Health Post-Fix

```
quantbackengine-nginx     healthy   (FailingStreak: 0)
quantbackengine-frontend  healthy
quantbackengine-backend   healthy
quantbackengine-db        healthy
```

---

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
