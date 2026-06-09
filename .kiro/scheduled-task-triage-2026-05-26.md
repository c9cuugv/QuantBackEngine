# Scheduled E2E Triage — 2026-05-26

## BLOCKER: Disk Full

**Root cause**: `/Users/deep` macOS system volume is at 0 bytes free.  
**Impact**: ALL execution tools fail with `ENOSPC`:
- `ctx_batch_execute` → `mkdtemp /var/folders/51/.../T/ctx-mode-XXXXXX` fails
- `ctx_execute` → same temp dir failure
- `Bash` → `mkdir /Users/deep/.claude/session-env/...` fails
- `TaskCreate` → `lstat /Users/deep/.claude/tasks/.../.lock` fails

**Action required**: Free space on the macOS system volume (likely `/dev/disk1s1` or similar).  
Common culprits: `~/Library/Developer/Xcode/DerivedData`, `~/.gradle/caches`, Docker images, large log files.

---

## Project State (read-only audit)

### E2E Tests Found
| File | Tests |
|------|-------|
| `frontend/tests/e2e/ui/dashboard.spec.ts` | 5 — page load, symbol selector, strategy selector, run button, metrics cards |
| `frontend/tests/e2e/api/` | **MISSING** — no api spec files found at expected path |

### Playwright Config (`frontend/playwright.config.ts`)
- `testDir: ./tests/e2e`
- Port: 3001 (avoids collision)
- `webServer`: `npm run dev -- -p 3001`, API at `http://localhost:8080`
- No previous `playwright-results.json` found (no prior run artifacts)

### Backend Unit Tests
- `BacktestServiceTest.java` — 4 tests: TA4J success path, fct: routing, non-fct routing, unknown fct throws
- `MarketDataServicePerformanceTest.java` — modified (not inspected)

### Recent Commits
```
653fa24 test(e2e): expand API coverage + fix Python bridge Docker misconfiguration
9146b38 perf: route /api/ direct to Spring Boot + log rotation + Hikari pool tuning
bcd0784 test(e2e): fix UI test failures caused by port conflict and wrong selectors
1d5b1ab test: add BacktestController and BollingerLiveCalculator coverage + fix @CrossOrigin
c69dc5e refactor: extract MetricsCalculator and validate StrategyDto
```

### Modified Files (unstaged)
Backend: `Dockerfile`, `pom.xml`, `QuantBackEngineApplication`, `MarketDataController`, `BacktestService`, `MarketDataService`, `StrategyRegistry`, `application*.properties`, test files  
Frontend: `Dockerfile`, `api/[...path]/route.ts`, `next.config.js`, `package*.json`

New untracked: `AnalyticsController`, `AnalyticsService`, `DefaultAnalyticsService`, `PythonBridgeService`, `BollingerLiveCalculator`, Python bridge config + exception classes, several DTOs

---

## Scheduled Task — Pending Steps

Once disk space is freed, re-run the scheduled task. It will:

1. **E2E baseline** — `cd frontend && npx playwright test`
2. **On failure** — search GitHub issues for matching stack trace; create new issue if none
3. **Fix + coverage** — apply fix, verify test gaps
4. **Impact scan** — check downstream dependencies
5. **Review** — analyze staged diff for logic flaws
6. **Final E2E** — confirm all green
7. **Commit** — conventional commit with architectural reasoning

---

## Triage Run — 2026-06-09

**Status: ✅ 17/17 PASS — no regressions**

| Suite | Tests | Result |
|-------|-------|--------|
| `api/endpoints.spec.ts` | 12 | ✅ all pass |
| `ui/dashboard.spec.ts` | 5 | ✅ all pass |

**Duration:** 8.5s  
**Stack:** Docker (nginx:80 → backend:8080, db:5432, frontend:3000) — all 4 services healthy (up 4 days)  
**No failures → no GitHub issue filed, no fix required.**

---

## Quick Retry Commands

```bash
# Check disk usage
du -sh ~/Library/Developer/Xcode/DerivedData 2>/dev/null
du -sh ~/.gradle/caches 2>/dev/null
docker system df 2>/dev/null

# Free space (safe)
rm -rf ~/Library/Developer/Xcode/DerivedData
rm -rf ~/.gradle/caches/modules-*/files-*/

# Then re-run e2e from frontend dir
cd '/Volumes/APPLE HDD ST2000DM001 Media/Projects/personal_project/new_learning/QuantBackEngine/frontend'
npx playwright test --reporter=list
```
