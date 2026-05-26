# Requirements: FinceptTerminal Integration

## Introduction

Python bridge layer in QuantBackEngine's Spring Boot backend that invokes FinceptTerminal scripts via `ProcessBuilder`, parses JSON stdout, and exposes results through new/extended REST endpoints.

## Glossary

- **PythonBridgeService**: Subprocess manager — spawns, times out, and parses Python script output.
- **PythonMarketDataProvider**: Maps source names to script paths; returns OHLCV bars.
- **PythonStrategyAdapter**: `TradingStrategy` implementation delegating to `python_backtest_engine.py`.
- **AnalyticsService**: Calls quantstats/Monte Carlo scripts and returns metrics.
- **fct: prefix**: Strategy ID prefix routing to `PythonStrategyAdapter` instead of TA4J.
- **OhlcvBar**: Single OHLCV data point: symbol, timestamp, open, high, low, close, volume.

---

## Requirements

### Requirement 1: Python Bridge Subprocess Management

**User Story:** As a backend developer, I want a single managed subprocess layer, so that all Python script invocations are consistent, safe, and observable.

#### Acceptance Criteria

1. WHEN `invoke()` is called with a valid script path and args, THE `PythonBridgeService` SHALL spawn a subprocess, read stdout, and return a parsed `JsonNode`.
2. WHEN a script path contains `..` segments, THE `PythonBridgeService` SHALL reject the invocation with `PythonBridgeException` before spawning any process.
3. WHEN a subprocess exceeds `fincept.python.timeout-seconds`, THE `PythonBridgeService` SHALL call `destroyForcibly()` and throw `PythonBridgeException`.
4. WHEN a subprocess exits with a non-zero code or produces empty stdout, THE `PythonBridgeService` SHALL throw `PythonBridgeException` and log stderr at WARN.
5. THE `PythonBridgeService` SHALL drain stderr asynchronously so stdout and stderr are never mixed.
6. WHEN `isAvailable()` is called, THE `PythonBridgeService` SHALL return a cached result valid for 30 seconds without re-spawning a process.
7. THE `PythonBridgeService` SHALL never throw from `isAvailable()` — Python absence is treated as `false`.

---

### Requirement 2: Python Market Data (Primary Source)

**User Story:** As a user, I want market data fetched live from Python sources for any symbol, so that I am not limited to pre-loaded CSV files.

#### Acceptance Criteria

1. WHEN market data is requested, THE `MarketDataService` SHALL fetch data exclusively via the Python bridge using `yfinance` as the default source — no CSV files are used.
2. WHEN `isAvailable()=false`, THE `MarketDataService` SHALL return an empty `BarSeries` and log WARN without spawning any subprocess.
3. WHEN the Python bridge returns data, THE `PythonMarketDataProvider` SHALL return bars sorted ascending by timestamp.
4. THE `PythonMarketDataProvider` SHALL only return `OhlcvBar` records where `high >= max(open, close)`, `low <= min(open, close)`, and `volume >= 0`.
5. WHEN the Python bridge returns no bars, THE `MarketDataService` SHALL return an empty `BarSeries` and log WARN.

---

### Requirement 3: Python Data Source Discovery

**User Story:** As a user, I want to see which Python data sources are available, so that I can choose the right source for my backtest.

#### Acceptance Criteria

1. WHEN `GET /api/v1/market-data/sources` is called, THE `MarketDataController` SHALL return a list of `DataSourceInfo` objects with `id`, `displayName`, `scriptPath`, and `available` fields.
2. WHEN `GET /api/v1/market-data/python/{symbol}` is called with valid parameters, THE `MarketDataController` SHALL return OHLCV bars via the Python bridge.
3. IF the Python bridge is unavailable or returns no data, THEN THE `MarketDataController` SHALL return HTTP 400 with a descriptive message.

---

### Requirement 4: Python Strategy Execution

**User Story:** As a user, I want to run FinceptTerminal Python strategies alongside Java strategies, so that I can access a broader strategy library.

#### Acceptance Criteria

1. WHEN a backtest request contains a strategy ID with the `fct:` prefix, THE `BacktestService` SHALL route execution to `PythonStrategyAdapter`.
2. WHEN a backtest request contains a strategy ID without the `fct:` prefix, THE `BacktestService` SHALL use the existing TA4J path and SHALL NOT invoke the Python bridge.
3. WHEN an `fct:` strategy ID is not registered, THE `BacktestService` SHALL return HTTP 400.
4. WHEN `PythonStrategyAdapter` receives `success=false` from the script, THE `PythonStrategyAdapter` SHALL throw `BacktestException` with the script's error message.
5. THE `PythonStrategyAdapter` SHALL map all `BacktestResponse` metric fields to non-null values, defaulting to `0.0` when omitted by the script.

---

### Requirement 5: Strategy Registry Loading

**User Story:** As a developer, I want Python strategies auto-registered at startup, so that they appear alongside Java strategies without manual configuration.

#### Acceptance Criteria

1. WHEN the application starts, THE `PythonStrategyRegistryLoader` SHALL read `registry_index.json` from the scripts base path and register a `PythonStrategyAdapter` for each entry.
2. IF `registry_index.json` is missing or malformed, THEN THE `PythonStrategyRegistryLoader` SHALL log WARN and register zero Python strategies without failing startup.

---

### Requirement 6: Analytics Enrichment

**User Story:** As a user, I want quantstats and Monte Carlo analytics on my backtest results, so that I can evaluate risk and performance beyond basic metrics.

#### Acceptance Criteria

1. WHEN `POST /api/v1/analytics/quantstats` is called with a valid `QuantstatsRequest`, THE `AnalyticsController` SHALL return a `QuantstatsResult` with `success=true` and populated `data`.
2. THE `AnalyticsService` SHALL never throw — any script failure SHALL be captured as `QuantstatsResult(success=false, ...)`.
3. WHEN the Python bridge is unavailable, THE `AnalyticsService` SHALL return `QuantstatsResult(success=false)` without spawning a process.

---

### Requirement 7: Configuration

**User Story:** As an operator, I want all Python integration settings in `application.properties`, so that I can tune or disable the bridge without code changes.

#### Acceptance Criteria

1. THE `PythonBridgeService` SHALL read `fincept.scripts.base-path`, `fincept.python.executable`, and `fincept.python.timeout-seconds` from application properties.

---

### Requirement 8: Backward Compatibility

**User Story:** As a user, I want all existing endpoints and strategies to keep working unchanged, so that the integration is purely additive.

#### Acceptance Criteria

1. THE `BacktestController` SHALL accept `POST /api/v1/backtest/run` with the same request/response contract as before this integration.
2. WHILE `isAvailable()=false`, THE system SHALL serve all non-Python backtest and market data requests without degradation.
3. THE system SHALL introduce no new Maven dependencies — `ProcessBuilder` and Jackson (already present) are sufficient.
