# Scheduled E2E Triage — 2026-06-02

## Status: ALL PASS ✓

**Run date**: 2026-06-02  
**Duration**: 11.2s  
**Result**: 17/17 passed — no failures, no regressions

---

## Infrastructure

| Container | Status | Port |
|-----------|--------|------|
| quantbackengine-nginx | healthy (2d) | 0.0.0.0:80→80 |
| quantbackengine-frontend | healthy (2d) | 3000/tcp (internal) |
| quantbackengine-backend | healthy (2d) | 8080/tcp (internal) |
| quantbackengine-db | healthy (5d) | 5432/tcp (internal) |

**Routing**: nginx proxies `/api/` → Spring Boot, `/` → Next.js  
**Test env**: `BASE_URL=http://localhost BACKEND_URL=http://localhost`

---

## Test Results

### API Suite — 12/12

| # | Test | Result |
|---|------|--------|
| 1 | GET /api/v1/backtest/strategies returns non-empty list | ✓ 95ms |
| 2 | GET /api/v1/backtest/strategies/{id} returns strategy detail | ✓ 132ms |
| 3 | GET /api/v1/backtest/strategies/{id} returns 404 for unknown id | ✓ 20ms |
| 4 | GET /api/v1/market-data/symbols returns list | ✓ 13ms |
| 5 | GET /api/v1/market-data/sources returns sources list | ✓ 97ms |
| 6 | POST /api/v1/backtest/run with SMA_CROSSOVER returns metrics | ✓ 1.1s |
| 7 | POST /api/v1/backtest/run with invalid payload returns 400 | ✓ 194ms |
| 8 | Backend health: strategies endpoint returns 200 | ✓ 10ms |
| 9 | GET /api/v1/market-data/python/{symbol} returns 200 or 400 (bridge-dependent) | ✓ 81ms |
| 10 | POST /api/v1/analytics/quantstats always returns 200 with success field | ✓ 82ms |
| 11 | GET /api/v1/data/symbols returns list | ✓ 34ms |
| 12 | POST /api/v1/data/upload + DELETE lifecycle | ✓ 170ms |

### UI Suite — 5/5

| # | Test | Result |
|---|------|--------|
| 13 | page loads and shows QuantBackEngine heading | ✓ 2.0s |
| 14 | symbol selector pre-filled with AAPL | ✓ 1.5s |
| 15 | strategy selector visible | ✓ 1.1s |
| 16 | run backtest button is present | ✓ 1.1s |
| 17 | metrics cards render after backtest | ✓ 2.3s |

---

## Actions Taken

- No test failures → no GitHub issue created
- No code changes → no fix applied
- No coverage gaps identified
- No downstream regressions detected

## Prior Triage Reference

- 2026-05-26: BLOCKER (disk full) — tests could not execute
- 2026-06-01: 17/17 pass (commit 25fa8f3)
- 2026-06-02: 17/17 pass — stable baseline confirmed
