# Codebase Concerns

**Analysis Date:** 2024-05-22

## Tech Debt

**Frontend God Component:**
- Issue: `frontend/app/page.tsx` (440 lines) manages state, data fetching, layout rendering for the entire dashboard, and modals.
- Files: `frontend/app/page.tsx`
- Impact: High complexity, difficult to maintain or test individual parts of the UI.
- Fix approach: Extract configuration logic to `ConfigPanel.tsx`, results to `ResultsDashboard.tsx`, and API logic into custom hooks (e.g., `useBacktest`, `useStrategies`).

**Manual Trade Simulation:**
- Issue: `BacktestService.java` manually loops through bars to simulate trades and calculate equity, even though `ta4j` is used for strategy execution.
- Files: `backend/src/main/java/com/quantbackengine/backend/service/BacktestService.java`
- Impact: Redundant logic that is prone to errors and doesn't leverage the full power of the `ta4j` library (e.g., `AnalysisCriterion`).
- Fix approach: Refactor to use `ta4j`'s built-in analysis criteria for performance metrics and trade processing.

**Redundant Symbol Listing:**
- Issue: Logic for listing available symbols is duplicated between the controller and the service.
- Files: `backend/src/main/java/com/quantbackengine/backend/controller/FileUploadController.java`, `backend/src/main/java/com/quantbackengine/backend/service/MarketDataService.java`
- Impact: Maintenance overhead; changes in symbol discovery must be updated in multiple places.
- Fix approach: Centralize all symbol discovery logic in `MarketDataService`.

## Security Considerations

**API Open by Default:**
- Issue: The backend uses `permitAll()` for all requests, relying entirely on Docker network isolation for security.
- Files: `backend/src/main/java/com/quantbackengine/backend/config/SecurityConfig.java`
- Risk: If the backend port (8080) is accidentally exposed to the host or public network, the API is fully accessible without authentication.
- Current mitigation: Network isolation via Docker Compose (port 8080 not mapped to host).
- Recommendations: Implement at least basic API key authentication or JWT-based auth between the frontend proxy and the backend.

**Exposure of Internal Details:**
- Issue: The Next.js API proxy returns detailed error strings from catches.
- Files: `frontend/app/api/[...path]/route.ts`
- Risk: Potential leakage of internal backend hostnames, IP addresses, or stack traces to the end-user.
- Current mitigation: None.
- Recommendations: Sanitize error responses in the proxy and return generic error messages for 5xx errors.

**Hardcoded Default Credentials:**
- Issue: Default security password `quantpass123` is committed in the configuration file.
- Files: `backend/src/main/resources/application.properties`
- Risk: Easy target for attackers if the application is deployed with default settings.
- Recommendations: Remove default values and require environment variables for all sensitive configurations.

## Performance Bottlenecks

**Unbounded In-Memory Cache:**
- Issue: `MarketDataService` uses an unbounded `ConcurrentHashMap` to cache `BarSeries` data.
- Files: `backend/src/main/java/com/quantbackengine/backend/service/MarketDataService.java`
- Cause: Every unique symbol queried is cached indefinitely.
- Improvement path: Replace with a bounded cache implementation like Caffeine or use `SoftReference` values to allow GC pressure to clear the cache.

**Inefficient File Counting:**
- Issue: `countCsvRows` reads the entire file line-by-line just to provide a row count in the upload response.
- Files: `backend/src/main/java/com/quantbackengine/backend/controller/FileUploadController.java`
- Cause: `Files.lines(filePath).count()`.
- Improvement path: Either eliminate the row count from the immediate response or store it as metadata if it's truly needed.

**Non-Streaming Proxy:**
- Issue: The Next.js API proxy reads the entire request body into memory (`req.text()`) and the entire response into memory (`upstream.arrayBuffer()`).
- Files: `frontend/app/api/[...path]/route.ts`
- Cause: Buffered I/O instead of streaming.
- Improvement path: Refactor to use `ReadableStream` to pipe requests and responses, especially for large CSV uploads/downloads.

## Fragile Areas

**CSV Parsing Logic:**
- Issue: `parseCSV` expects specific, hardcoded column names ("Date", "Open", etc.) and uses a simple `LocalDate.parse`.
- Files: `backend/src/main/java/com/quantbackengine/backend/service/MarketDataService.java`
- Why fragile: Different stock data providers use different headers (e.g., "Timestamp", "Adj Close") and date formats.
- Safe modification: Implement flexible header mapping and date format detection.

**H2 Console in Production:**
- Issue: H2 console is enabled by default in the main `application.properties`.
- Files: `backend/src/main/resources/application.properties`
- Risk: If deployed to production without overriding this, the database console might be accessible.
- Safe modification: Move H2 console and `create-drop` settings to `application-dev.properties`.

## Test Coverage Gaps

**Strategy Logic:**
- Issue: No unit tests found for individual trading strategies.
- Files: `backend/src/main/java/com/quantbackengine/backend/strategy/*.java`
- Risk: Algorithmic errors in strategy implementation (e.g., RSI calculation or SMA crossover logic) could lead to incorrect backtest results.
- Priority: High

**Market Data Resilience:**
- Issue: Missing tests for malformed CSV files or edge cases in date filtering.
- Files: `backend/src/main/java/com/quantbackengine/backend/service/MarketDataService.java`
- Risk: Application crash when processing unexpected data formats.
- Priority: Medium

---

*Concerns audit: 2024-05-22*
