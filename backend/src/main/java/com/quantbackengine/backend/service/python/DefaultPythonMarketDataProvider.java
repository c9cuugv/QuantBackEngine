package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.JsonNode;
import com.quantbackengine.backend.dto.DataSourceInfo;
import com.quantbackengine.backend.dto.OhlcvBar;
import com.quantbackengine.backend.exception.PythonBridgeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link PythonMarketDataProvider}.
 *
 * <p>Maps well-known source identifiers to script paths under the configured base path,
 * delegates invocation to {@link PythonBridgeService}, and post-processes results:
 * <ul>
 *   <li>Filters bars that violate the OHLCV invariant</li>
 *   <li>Sorts surviving bars ascending by timestamp</li>
 * </ul>
 */
@Service
@Slf4j
public class DefaultPythonMarketDataProvider implements PythonMarketDataProvider {

    /** Ordered map of source-id → relative script path. */
    private static final Map<String, SourceMeta> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("yfinance",       new SourceMeta("Yahoo Finance",    "market_data/yfinance_data.py"));
        SOURCES.put("fred",           new SourceMeta("FRED",             "market_data/fred_data.py"));
        SOURCES.put("akshare",        new SourceMeta("AkShare",          "market_data/akshare_data.py"));
        SOURCES.put("alpha_vantage",  new SourceMeta("Alpha Vantage",    "market_data/alpha_vantage_data.py"));
    }

    private record SourceMeta(String displayName, String scriptPath) {}

    private final PythonBridgeService bridge;

    public DefaultPythonMarketDataProvider(PythonBridgeService bridge) {
        this.bridge = bridge;
    }

    // -------------------------------------------------------------------------
    // PythonMarketDataProvider
    // -------------------------------------------------------------------------

    @Override
    public List<OhlcvBar> fetchHistorical(String symbol, LocalDate start, LocalDate end, String source) {
        SourceMeta meta = SOURCES.get(source);
        if (meta == null) {
            log.warn("Unknown market data source '{}', returning empty list", source);
            return List.of();
        }

        List<String> args = List.of("historical", symbol, start.toString(), end.toString());

        JsonNode json;
        try {
            json = bridge.invoke(meta.scriptPath(), args);
        } catch (PythonBridgeException e) {
            log.warn("Python bridge error fetching {} from {}: {}", symbol, source, e.getMessage());
            return List.of();
        }

        return parseBars(symbol, json);
    }

    @Override
    public List<DataSourceInfo> listSources() {
        boolean pythonAvailable = bridge.isAvailable();
        List<DataSourceInfo> result = new ArrayList<>(SOURCES.size());
        for (Map.Entry<String, SourceMeta> entry : SOURCES.entrySet()) {
            result.add(new DataSourceInfo(
                    entry.getKey(),
                    entry.getValue().displayName(),
                    entry.getValue().scriptPath(),
                    pythonAvailable
            ));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parse a JSON array of bar objects into a filtered, sorted list of {@link OhlcvBar}.
     */
    private List<OhlcvBar> parseBars(String symbol, JsonNode json) {
        if (!json.isArray()) {
            log.warn("Expected JSON array from market data script, got: {}", json.getNodeType());
            return List.of();
        }

        List<OhlcvBar> bars = new ArrayList<>();
        for (JsonNode node : json) {
            OhlcvBar bar = parseBar(symbol, node);
            if (bar == null) {
                continue;
            }
            if (!bar.isValid()) {
                log.debug("Dropping bar for {} at ts={} — OHLCV invariant violated", symbol, bar.timestamp());
                continue;
            }
            bars.add(bar);
        }

        bars.sort(Comparator.comparingLong(OhlcvBar::timestamp));
        return bars;
    }

    /**
     * Parse a single JSON object into an {@link OhlcvBar}, returning {@code null} on error.
     */
    private OhlcvBar parseBar(String symbol, JsonNode node) {
        try {
            long timestamp = node.get("timestamp").asLong();
            double open    = node.get("open").asDouble();
            double high    = node.get("high").asDouble();
            double low     = node.get("low").asDouble();
            double close   = node.get("close").asDouble();
            long volume    = node.get("volume").asLong();

            // Use symbol from JSON if present, otherwise fall back to the requested symbol
            String barSymbol = node.has("symbol") ? node.get("symbol").asText(symbol) : symbol;

            return new OhlcvBar(barSymbol, timestamp, open, high, low, close, volume);
        } catch (Exception e) {
            log.debug("Skipping malformed bar node: {} — {}", node, e.getMessage());
            return null;
        }
    }
}
