# Design: FinceptTerminal Integration

## Overview

Python bridge layer in the Spring Boot backend that calls FinceptTerminal's Python scripts via `ProcessBuilder`, parses their JSON stdout, and exposes results through new/extended REST endpoints. CSV-based market data loading is replaced entirely by live Python data sources (yfinance by default). Java TA4J strategies and all existing endpoints remain unchanged.

Three phases: (1) Python market data (primary source), (2) Python strategies alongside Java ones, (3) quantstats/Monte Carlo analytics.

## Architecture

```
Frontend (Next.js)
    └── BacktestController / MarketDataController / AnalyticsController
            └── BacktestService / MarketDataService / AnalyticsService
                    └── PythonBridgeService  ──ProcessBuilder──►  FinceptTerminal/fincept-qt/scripts/
                            ├── PythonMarketDataProvider  →  yfinance_data.py, fred_data.py, ...
                            ├── PythonStrategyAdapter     →  algo_trading/python_backtest_engine.py
                            └── AnalyticsService          →  Analytics/quantstats_analytics.py
```

**Data flow rule:** Python bridge is the sole market data source. CSV loading is removed. Python bridge only invoked when `isAvailable() = true`.

**Strategy routing:** `fct:` prefix in strategy ID → `PythonStrategyAdapter`. All other IDs → existing TA4J path.

## Components

| Component | Location | Purpose |
|---|---|---|
| `PythonBridgeService` | `service/python/` | Single subprocess manager — spawn, timeout, stdout/stderr, JSON parse |
| `PythonMarketDataProvider` | `service/python/` | Maps source name → script path, fetches OHLCV bars |
| `PythonStrategyAdapter` | `strategy/` | Implements `TradingStrategy`, delegates to `python_backtest_engine.py` |
| `PythonStrategyRegistryLoader` | `strategy/` | Reads `registry_index.json` at startup, registers adapters |
| `AnalyticsService` | `service/` | Calls `quantstats_analytics.py`, returns metrics map |
| `AnalyticsController` | `controller/` | New REST endpoints for analytics |

## Key Interfaces

```java
interface PythonBridgeService {
    JsonNode invoke(String scriptRelativePath, List<String> args) throws PythonBridgeException;
    JsonNode invokeWithStdin(String scriptRelativePath, List<String> args, String stdinJson) throws PythonBridgeException;
    boolean isAvailable(); // cached 30s, never throws
}

interface PythonMarketDataProvider {
    List<OhlcvBar> fetchHistorical(String symbol, LocalDate start, LocalDate end, String source);
    List<DataSourceInfo> listSources();
}

interface AnalyticsService {
    QuantstatsResult runQuantstats(QuantstatsRequest request); // never throws
}
```

## Data Models

```java
record OhlcvBar(String symbol, long timestamp, double open, double high, double low, double close, long volume) {}
// Invariant: high >= max(open,close), low <= min(open,close), volume >= 0

record DataSourceInfo(String id, String displayName, String scriptPath, boolean available) {}

record QuantstatsRequest(Map<String, Double> tickersWeights, String benchmark, String period,
                         double riskFreeRate, String action) {}
// action: "stats" | "returns" | "drawdown" | "rolling" | "montecarlo" | "full_report"

record QuantstatsResult(boolean success, String action, Map<String, Object> data) {}
```

## Core Logic

**`PythonBridgeService.invoke()`**
1. Resolve script path under `fincept.scripts.base-path` — reject `..` segments
2. `ProcessBuilder([python, script] + args)`, stderr drained async
3. Read stdout with timeout; `destroyForcibly()` on timeout
4. Exit code ≠ 0 or empty stdout → throw `PythonBridgeException`
5. `objectMapper.readTree(stdout)` → return `JsonNode`

**`MarketDataService.getBarSeries()` — rewritten**
```
1. If !pythonBridge.isAvailable():
       log WARN, return empty BarSeries
2. bars = pythonMarketDataProvider.fetchHistorical(symbol, start, end, "yfinance")
3. If bars empty → log WARN, return empty BarSeries
4. return convertToBarSeries(bars)
```

**`BacktestService.runBacktest()` — extended**
```
strategy = registry.getStrategy(request.strategy).orElseThrow()
if strategy instanceof PythonStrategyAdapter:
    return pythonStrategy.runPythonBacktest(request)   // Python path
else:
    // existing TA4J path — unchanged
```

**`PythonStrategyAdapter.runPythonBacktest()`**
```
args = [--strategy-id, fctId, --symbols, symbol, --start-date, ..., --end-date, ...,
        --initial-cash, ..., --parameters, jsonParams]
json = bridge.invoke("algo_trading/python_backtest_engine.py", args)
if !json["success"] → throw BacktestException(json["error"])
return mapToBacktestResponse(json["trades"], json["equity_curve"], json["metrics"])
```

## API Endpoints (new)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/market-data/python/{symbol}?source=yfinance&start=&end=` | Fetch OHLCV via Python |
| `GET` | `/api/v1/market-data/sources` | List available Python data sources |
| `POST` | `/api/v1/analytics/quantstats` | Run quantstats analytics |

Existing `POST /api/v1/backtest/run` unchanged — `fct:` prefix in `strategy` field activates Python path.

## Configuration

```properties
fincept.scripts.base-path=FinceptTerminal/fincept-qt/scripts
fincept.python.executable=python3
fincept.python.timeout-seconds=60
```

## Error Handling

| Scenario | Behaviour |
|---|---|
| Python not found / bad path | `isAvailable()=false`; all Python paths skipped; Java strategies unaffected |
| Script timeout | Process killed; HTTP 500 with message; no thread leak |
| Invalid JSON / empty stdout | `PythonBridgeException`; stderr logged at WARN |
| Unknown `fct:` strategy ID | `Optional.empty()` → HTTP 400 |
| Empty data from source | Empty `BarSeries` → HTTP 400 "No market data available" |

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — a formal statement about what the system should do.*

### Property 1: Python bars are sorted

*For any* valid OHLCV JSON response from a Python data script, the resulting `OhlcvBar` list SHALL be sorted in ascending order by timestamp.

**Validates: Requirement 2.3**

### Property 2: OhlcvBar OHLCV invariant

*For any* `OhlcvBar` returned by `fetchHistorical()`, `high >= max(open, close)`, `low <= min(open, close)`, and `volume >= 0` SHALL hold.

**Validates: Requirement 2.4**

### Property 3: fct: prefix routes to PythonStrategyAdapter

*For any* strategy ID beginning with `fct:` that is registered, the `BacktestService` SHALL route execution to a `PythonStrategyAdapter` and SHALL NOT invoke the TA4J path.

**Validates: Requirements 4.1, 4.2**

### Property 4: Non-fct: strategies never touch the Python bridge

*For any* strategy ID that does not begin with `fct:`, the `PythonBridgeService` SHALL not be invoked during backtest execution.

**Validates: Requirement 4.2**

### Property 5: BacktestResponse metrics are non-null

*For any* JSON response from `python_backtest_engine.py`, all `BacktestResponse` metric fields SHALL be non-null, defaulting to `0.0` when the Python script omits them.

**Validates: Requirement 4.5**

### Property 6: runQuantstats() never throws

*For any* `QuantstatsRequest` input, including malformed or empty inputs, `AnalyticsService.runQuantstats()` SHALL return a `QuantstatsResult` and SHALL NOT propagate an exception.

**Validates: Requirement 6.2**

### Property 7: isAvailable()=false means zero subprocesses

*For any* call to any Python-backed method while `isAvailable()=false`, the `PythonBridgeService` SHALL spawn zero subprocesses.

**Validates: Requirements 1.7, 2.2, 6.3**

### Property 8: Path traversal is always rejected

*For any* script path argument containing `..` segments, `PythonBridgeService.invoke()` SHALL throw `PythonBridgeException` without spawning a process.

**Validates: Requirement 1.2**

### Property 9: Non-zero exit or empty stdout always throws

*For any* subprocess invocation that exits with a non-zero code or produces empty stdout, `PythonBridgeService` SHALL throw `PythonBridgeException`.

**Validates: Requirement 1.4**

## Testing

- **Unit:** Mock `ProcessBuilder` for bridge; mock bridge for provider/adapter/analytics
- **Property (jqwik):** Any valid OHLCV JSON → bars sorted by timestamp; any `BacktestResponse` from adapter → all metrics finite
- **Integration:** Real `yfinance_data.py` call returns ≥ 50 bars; real `quantstats_analysis.py` returns finite Sharpe

## Dependencies & Notes

- No new Maven deps — `ProcessBuilder` is JDK, Jackson already in Spring Boot starter
- Python deps (`yfinance`, `quantstats`, `pandas`, `numpy`) already in `requirements-numpy2.txt`
- Scripts are read-only — never modified by the Java layer
- API keys for paid sources (Polygon, Alpha Vantage) passed via env vars, never hardcoded
