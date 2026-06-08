# E2E Triage — 2026-06-08

**Scheduled task:** senior-e2e-integration-and-triage-loop  
**Run time:** 2026-06-08T12:28–12:29 UTC  
**Stack:** Docker (quantbackengine-nginx :80, -frontend, -backend, -db — all healthy)

## Summary

| Total | Passed | Failed | Skipped | Flaky | Duration |
|-------|--------|--------|---------|-------|----------|
| 17    | 17     | 0      | 0       | 0     | 11.2s    |

## Test Results

| Suite | Test | Status |
|-------|------|--------|
| Backend API — Baseline | GET /api/v1/backtest/strategies returns non-empty list | ✅ |
| Backend API — Baseline | GET /api/v1/backtest/strategies/{id} returns strategy detail | ✅ |
| Backend API — Baseline | GET /api/v1/backtest/strategies/{id} returns 404 for unknown id | ✅ |
| Backend API — Baseline | GET /api/v1/market-data/symbols returns list | ✅ |
| Backend API — Baseline | GET /api/v1/market-data/sources returns sources list | ✅ |
| Backend API — Baseline | POST /api/v1/backtest/run with SMA_CROSSOVER returns metrics | ✅ |
| Backend API — Baseline | POST /api/v1/backtest/run with invalid payload returns 400 | ✅ |
| Backend API — Baseline | Backend health: strategies endpoint returns 200 | ✅ |
| Backend API — Baseline | GET /api/v1/market-data/python/{symbol} returns 200 or 400 (bridge-dependent) | ✅ |
| Backend API — Baseline | POST /api/v1/analytics/quantstats always returns 200 with success field | ✅ |
| Backend API — Baseline | GET /api/v1/data/symbols returns list | ✅ |
| Backend API — Baseline | POST /api/v1/data/upload + DELETE lifecycle | ✅ |
| Dashboard — Baseline UI | page loads and shows QuantBackEngine heading | ✅ |
| Dashboard — Baseline UI | symbol selector pre-filled with AAPL | ✅ |
| Dashboard — Baseline UI | strategy selector visible | ✅ |
| Dashboard — Baseline UI | run backtest button is present | ✅ |
| Dashboard — Baseline UI | metrics cards render after backtest | ✅ |

## Actions

- No failures → no GitHub issues filed, no fixes applied
- CLAUDE.md cleaned: removed duplicate Context7 section + shell error artifact lines appended by hook
- backend/Dockerfile migrated from system `mvn` to `./mvnw` (Maven wrapper) — improves reproducibility, removes Alpine `mvn` apk dependency
- backend/.dockerignore tightened: excludes `*.log` and `.mvn/wrapper/maven-wrapper.jar`
- docker-compose.yml / frontend/Dockerfile: minor infra tweaks from prior session

## Previous triage

- 2026-06-07: 17/17 pass (commit b80ba88)
