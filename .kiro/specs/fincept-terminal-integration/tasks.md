# Implementation Plan: FinceptTerminal Integration

## Overview

Additive Python bridge layer for the Spring Boot backend. Tasks are ordered by dependency: bridge infrastructure first, then consumers (market data, strategies, analytics), then wiring and endpoints. No existing code is removed or broken.

## Tasks

- [x] 1. Add configuration properties and exception class
  - Add `fincept.scripts.base-path`, `fincept.python.executable`, `fincept.python.timeout-seconds`, and `fincept.python.market-data.enabled` to `backend/src/main/resources/application.properties`
  - Create `PythonBridgeException` (unchecked) in `exception/`
  - Create `@ConfigurationProperties`-bound `PythonBridgeProperties` record/class in `config/`
  - _Requirements: 7.1, 8.3_

- [x] 2. Implement `PythonBridgeService`
  - [x] 2.1 Create `PythonBridgeService` interface and `DefaultPythonBridgeService` implementation in `service/python/`
    - Implement `invoke()`: resolve path under base-path, reject `..` segments, spawn via `ProcessBuilder`, drain stderr async on a virtual/daemon thread, read stdout with timeout, `destroyForcibly()` on timeout, parse JSON with Jackson
    - Implement `invokeWithStdin()`: same as `invoke()` but write `stdinJson` to process stdin before reading stdout
    - Implement `isAvailable()`: run `python --version` (or configured executable), cache result for 30 s, never throw
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

  - [x] 2.2 Write property test for path traversal rejection (Property 9)
    - **Property 9: Path traversal is always rejected**
    - **Validates: Requirement 1.2**
    - Use jqwik `@ForAll` to generate strings containing `..` and assert `PythonBridgeException` is thrown without spawning a process

  - [x] 2.3 Write property test for non-zero exit / empty stdout (Property 10)
    - **Property 10: Non-zero exit or empty stdout always throws**
    - **Validates: Requirement 1.4**
    - Use jqwik with a mock/stub `ProcessBuilder` to simulate non-zero exit codes and empty stdout; assert `PythonBridgeException` in all cases

  - [x] 2.4 Write unit tests for `DefaultPythonBridgeService`
    - Test timeout path: mock process that never finishes → assert `destroyForcibly()` called and exception thrown
    - Test `isAvailable()` caching: verify second call within 30 s does not spawn a new process
    - Test `isAvailable()` returns `false` when executable not found (no exception propagated)
    - _Requirements: 1.3, 1.6, 1.7_

- [x] 3. Checkpoint — bridge layer complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement `OhlcvBar`, `DataSourceInfo`, and `PythonMarketDataProvider`
  - [x] 4.1 Create `OhlcvBar` and `DataSourceInfo` records in `dto/`
    - `OhlcvBar(String symbol, long timestamp, double open, double high, double low, double close, long volume)`
    - `DataSourceInfo(String id, String displayName, String scriptPath, boolean available)`
    - _Requirements: 2.3, 2.4, 3.1_

  - [x] 4.2 Create `PythonMarketDataProvider` interface and `DefaultPythonMarketDataProvider` implementation in `service/python/`
    - Map source names (`yfinance`, `fred`, `akshare`, etc.) to script paths under the configured base path
    - Implement `fetchHistorical()`: call `bridge.invoke()`, parse JSON array into `OhlcvBar` list, filter bars failing OHLCV invariant, sort ascending by timestamp
    - Implement `listSources()`: return `DataSourceInfo` for each known source with `available` reflecting `bridge.isAvailable()`
    - _Requirements: 2.3, 2.4, 3.1_

  - [x] 4.3 Write property test for bars sorted by timestamp (Property 2)
    - **Property 2: Python fallback bars are sorted**
    - **Validates: Requirement 2.3**
    - Use jqwik to generate arbitrary lists of `OhlcvBar` JSON, feed through provider parsing logic, assert result is sorted ascending by timestamp

  - [x] 4.4 Write property test for OhlcvBar OHLCV invariant (Property 3)
    - **Property 3: OhlcvBar OHLCV invariant**
    - **Validates: Requirement 2.4**
    - Use jqwik to generate arbitrary OHLCV values; assert that only bars satisfying `high >= max(open,close)`, `low <= min(open,close)`, `volume >= 0` are returned

  - [x] 4.5 Write unit tests for `DefaultPythonMarketDataProvider`
    - Mock `PythonBridgeService` to return canned JSON; assert correct `OhlcvBar` mapping and sort order
    - Assert bars violating invariants are filtered out
    - _Requirements: 2.3, 2.4_

- [x] 5. Rewrite `MarketDataService` to use Python as primary data source
  - Remove all CSV loading logic (uploads folder, classpath resources, CSV parsing)
  - Remove `PythonBridgeProperties` market-data enabled flag — Python is always the source
  - `getBarSeries()`: if `bridge.isAvailable()`, call `fetchHistorical()` and convert to `BarSeries`; otherwise log WARN and return empty `BarSeries`
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 5.1 Write property test for isAvailable()=false means zero subprocesses (Property 7)
    - **Property 7: isAvailable()=false means zero subprocesses**
    - **Validates: Requirements 1.7, 2.2, 6.3**
    - Use jqwik; when bridge returns `isAvailable()=false`, assert no subprocess is spawned across all Python-backed methods

- [x] 6. Checkpoint — market data layer complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement `PythonStrategyAdapter` and `PythonStrategyRegistryLoader`
  - [x] 7.1 Create `PythonStrategyAdapter` in `strategy/`
    - Implement `TradingStrategy` interface; store `fctId` and script path
    - Implement `runPythonBacktest(BacktestRequest)`: build args list, call `bridge.invoke("algo_trading/python_backtest_engine.py", args)`, check `success` field, throw `BacktestException` on failure, map JSON to `BacktestResponse` with all metric fields defaulting to `0.0` when absent
    - _Requirements: 4.1, 4.4, 4.5_

  - [x] 7.2 Write property test for BacktestResponse metrics non-null (Property 6)
    - **Property 6: BacktestResponse metrics are non-null**
    - **Validates: Requirement 4.5**
    - Use jqwik to generate JSON responses with arbitrary missing metric fields; assert all `BacktestResponse` metric fields are non-null and finite (no `NaN`, no `Infinity`)

  - [x] 7.3 Write property test for fct: prefix routing (Property 4)
    - **Property 4: fct: prefix routes to PythonStrategyAdapter**
    - **Validates: Requirements 4.1, 4.2**
    - Use jqwik to generate strategy IDs starting with `fct:`; assert `BacktestService` routes to `PythonStrategyAdapter` and TA4J path is never invoked

  - [x] 7.4 Write property test for non-fct: strategies never touch bridge (Property 5)
    - **Property 5: Non-fct: strategies never touch the Python bridge**
    - **Validates: Requirement 4.2**
    - Use jqwik to generate strategy IDs not starting with `fct:`; assert `PythonBridgeService` is never called during backtest execution

  - [x] 7.5 Create `PythonStrategyRegistryLoader` in `strategy/`
    - Implement `ApplicationRunner`; at startup read `registry_index.json` from `fincept.scripts.base-path`
    - For each entry, instantiate `PythonStrategyAdapter` and register in `StrategyRegistry`
    - If file missing or malformed, log WARN and register zero strategies without failing startup
    - _Requirements: 5.1, 5.2_

  - [x] 7.6 Write unit tests for `PythonStrategyRegistryLoader`
    - Test happy path: valid `registry_index.json` → correct number of adapters registered
    - Test missing file → zero adapters, no exception
    - Test malformed JSON → zero adapters, WARN logged, no exception
    - _Requirements: 5.1, 5.2_

- [x] 8. Extend `BacktestService` with `fct:` routing
  - Inject `PythonStrategyAdapter` lookup (via `StrategyRegistry`) into `BacktestService`
  - Add prefix check: if `request.strategy` starts with `fct:`, retrieve adapter and call `runPythonBacktest()`; otherwise use existing TA4J path unchanged
  - If `fct:` ID not registered, throw exception that resolves to HTTP 400
  - _Requirements: 4.1, 4.2, 4.3, 8.1_

  - [x] 8.1 Write unit tests for `BacktestService` routing
    - Test `fct:` prefix → `PythonStrategyAdapter.runPythonBacktest()` called, TA4J not invoked
    - Test non-`fct:` prefix → existing TA4J path, bridge not invoked
    - Test unknown `fct:` ID → HTTP 400 response
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 9. Checkpoint — strategy layer complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement `AnalyticsService` and `AnalyticsController`
  - [x] 10.1 Create `QuantstatsRequest` and `QuantstatsResult` records in `dto/`
    - `QuantstatsRequest(Map<String,Double> tickersWeights, String benchmark, String period, double riskFreeRate, String action)`
    - `QuantstatsResult(boolean success, String action, Map<String,Object> data)`
    - _Requirements: 6.1_

  - [x] 10.2 Create `AnalyticsService` interface and `DefaultAnalyticsService` implementation in `service/`
    - Implement `runQuantstats(QuantstatsRequest)`: check `bridge.isAvailable()`, if false return `QuantstatsResult(false, ...)`; otherwise call `bridge.invoke("Analytics/quantstats_analytics.py", args)`, parse result; wrap all exceptions in `QuantstatsResult(false, ...)` — never propagate
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 10.3 Write property test for runQuantstats() never throws (Property 7)
    - **Property 7: runQuantstats() never throws**
    - **Validates: Requirement 6.2**
    - Use jqwik to generate arbitrary `QuantstatsRequest` inputs including null/empty fields; assert `runQuantstats()` always returns a `QuantstatsResult` and never throws

  - [x] 10.4 Create `AnalyticsController` in `controller/`
    - Implement `POST /api/v1/analytics/quantstats` accepting `QuantstatsRequest`, delegating to `AnalyticsService`, returning `QuantstatsResult`
    - _Requirements: 6.1_

  - [x] 10.5 Write unit tests for `AnalyticsService` and `AnalyticsController`
    - Test bridge unavailable → `QuantstatsResult(success=false)` returned, no subprocess spawned
    - Test script failure → `QuantstatsResult(success=false)`, no exception propagated
    - Test happy path: mock bridge returns valid JSON → `QuantstatsResult(success=true, data=...)`
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 11. Add new `MarketDataController` endpoints
  - Add `GET /api/v1/market-data/python/{symbol}?source=yfinance&start=&end=` to `MarketDataController`
    - Delegate to `PythonMarketDataProvider.fetchHistorical()`; return bars or HTTP 400 if bridge unavailable or no data
  - Add `GET /api/v1/market-data/sources` to `MarketDataController`
    - Delegate to `PythonMarketDataProvider.listSources()`; return list of `DataSourceInfo`
  - _Requirements: 3.1, 3.2, 3.3_

  - [x] 11.1 Write unit tests for new `MarketDataController` endpoints
    - Test `/python/{symbol}` with bridge unavailable → HTTP 400
    - Test `/python/{symbol}` with no data returned → HTTP 400
    - Test `/sources` → returns correct `DataSourceInfo` list
    - _Requirements: 3.1, 3.2, 3.3_

- [x] 12. Final checkpoint — wire everything together
  - Ensure all Spring beans are wired (no missing `@Autowired`/constructor injection)
  - Verify `application.properties` defaults are present and correct
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Property tests use jqwik (already available via `spring-boot-starter-test` transitive deps or add as test-scope only)
- All new classes go under `backend/src/main/java/com/quantbackengine/backend/`
- Scripts directory is read-only — Java layer never writes to `FinceptTerminal/fincept-qt/scripts/`
