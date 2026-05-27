package com.quantbackengine.backend.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.dto.BacktestResponse;
import com.quantbackengine.backend.dto.BacktestResponse.*;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TradingStrategy implementation that delegates backtest execution to a Python script
 * via PythonBridgeService. Instantiated programmatically by PythonStrategyRegistryLoader.
 *
 * <p>Do NOT annotate with @Component — this class is created programmatically.</p>
 */
@Slf4j
public class PythonStrategyAdapter implements TradingStrategy {

    private final String fctId;
    private final String scriptPath;
    private final PythonBridgeService bridge;
    private final ObjectMapper objectMapper;

    public PythonStrategyAdapter(String fctId, String scriptPath,
                                  PythonBridgeService bridge, ObjectMapper objectMapper) {
        this.fctId = fctId;
        this.scriptPath = scriptPath;
        this.bridge = bridge;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getId() {
        return "fct:" + fctId;
    }

    @Override
    public String getName() {
        return "Python Strategy: " + fctId;
    }

    @Override
    public String getDescription() {
        return "FinceptTerminal Python strategy '" + fctId + "' executed via python_backtest_engine.py";
    }

    @Override
    public Strategy buildStrategy(BarSeries series, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("PythonStrategyAdapter does not use TA4J");
    }

    @Override
    public List<ParameterDefinition> getParameterDefinitions() {
        return List.of();
    }

    /**
     * Run a backtest via the Python bridge.
     *
     * @param request the backtest request
     * @return populated BacktestResponse with all metric fields defaulting to 0.0 when absent
     */
    public BacktestResponse runPythonBacktest(BacktestRequest request) {
        List<String> args = buildArgs(request);

        log.info("Running Python backtest for strategy '{}' on symbol '{}'", fctId, request.getSymbol());

        JsonNode json = bridge.invoke("algo_trading/python_backtest_engine.py", args);

        if (!json.path("success").asBoolean(false)) {
            String error = json.path("error").asText("Python backtest failed");
            log.warn("Python backtest failed for strategy '{}': {}", fctId, error);
            throw new IllegalStateException(error);
        }

        return mapToBacktestResponse(json, request);
    }

    // -------------------------------------------------------------------------
    // Package-private for testing
    // -------------------------------------------------------------------------

    /**
     * Maps the metrics node to a MetricsDto, defaulting all fields to 0.0 when absent.
     * Package-private to allow direct testing.
     */
    MetricsDto mapMetrics(JsonNode metricsNode) {
        return MetricsDto.builder()
                .totalReturn(metricsNode.path("total_return").asDouble(0.0))
                .annualizedReturn(metricsNode.path("annualized_return").asDouble(0.0))
                .maxDrawdown(metricsNode.path("max_drawdown").asDouble(0.0))
                .maxDrawdownPercent(metricsNode.path("max_drawdown_percent").asDouble(0.0))
                .sharpeRatio(metricsNode.path("sharpe_ratio").asDouble(0.0))
                .backtestYears(metricsNode.path("backtest_years").asDouble(0.0))
                .totalTrades(metricsNode.path("total_trades").asInt(0))
                .winningTrades(metricsNode.path("winning_trades").asInt(0))
                .losingTrades(metricsNode.path("losing_trades").asInt(0))
                .winRate(metricsNode.path("win_rate").asDouble(0.0))
                .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> buildArgs(BacktestRequest request) {
        List<String> args = new ArrayList<>();
        args.add("--strategy-id");
        args.add(fctId);
        args.add("--symbol");
        args.add(request.getSymbol());
        args.add("--start-date");
        args.add(request.getStartDate().toString());
        args.add("--end-date");
        args.add(request.getEndDate().toString());
        args.add("--initial-cash");
        args.add(String.valueOf(request.getInitialCapital() != null ? request.getInitialCapital() : 100000.0));

        Map<String, Object> params = request.getParameters();
        if (params != null && !params.isEmpty()) {
            try {
                args.add("--parameters");
                args.add(objectMapper.writeValueAsString(params));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize parameters for strategy '{}': {}", fctId, e.getMessage());
            }
        }

        return args;
    }

    private BacktestResponse mapToBacktestResponse(JsonNode json, BacktestRequest request) {
        MetricsDto metrics = mapMetrics(json.path("metrics"));
        List<TradeDto> trades = mapTrades(json.path("trades"));
        List<EquityPointDto> equityCurve = mapEquityCurve(json.path("equity_curve"));
        List<CandleDto> candles = mapCandles(json.path("candles"));

        return BacktestResponse.builder()
                .id(UUID.randomUUID().toString())
                .symbol(request.getSymbol())
                .strategy(getId())
                .metrics(metrics)
                .trades(trades)
                .equityCurve(equityCurve)
                .candles(candles)
                .build();
    }

    private List<TradeDto> mapTrades(JsonNode tradesNode) {
        List<TradeDto> trades = new ArrayList<>();
        if (tradesNode == null || !tradesNode.isArray()) return trades;
        for (JsonNode t : tradesNode) {
            trades.add(TradeDto.builder()
                    .type(t.path("type").asText("ROUND_TRIP"))
                    .entryDate(parseDateTime(t.path("entry_date").asText(null)))
                    .entryPrice(t.path("entry_price").asDouble(0.0))
                    .exitDate(parseDateTime(t.path("exit_date").asText(null)))
                    .exitPrice(t.path("exit_price").asDouble(0.0))
                    .shares(t.path("shares").asDouble(0.0))
                    .pnl(t.path("pnl").asDouble(0.0))
                    .commission(t.path("commission").asDouble(0.0))
                    .build());
        }
        return trades;
    }

    private List<EquityPointDto> mapEquityCurve(JsonNode equityNode) {
        List<EquityPointDto> curve = new ArrayList<>();
        if (equityNode == null || !equityNode.isArray()) return curve;
        for (JsonNode e : equityNode) {
            curve.add(EquityPointDto.builder()
                    .timestamp(e.path("timestamp").asLong(0L))
                    .value(e.path("value").asDouble(0.0))
                    .build());
        }
        return curve;
    }

    private List<CandleDto> mapCandles(JsonNode candlesNode) {
        List<CandleDto> candles = new ArrayList<>();
        if (candlesNode == null || !candlesNode.isArray()) return candles;
        for (JsonNode c : candlesNode) {
            candles.add(CandleDto.builder()
                    .time(c.path("time").asLong(0L))
                    .open(c.path("open").asDouble(0.0))
                    .high(c.path("high").asDouble(0.0))
                    .low(c.path("low").asDouble(0.0))
                    .close(c.path("close").asDouble(0.0))
                    .volume(c.path("volume").asLong(0L))
                    .build());
        }
        return candles;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            log.debug("Could not parse datetime '{}': {}", value, e.getMessage());
            return null;
        }
    }
}
