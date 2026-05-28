package com.quantbackengine.backend.service;

import com.quantbackengine.backend.dto.OhlcvBar;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import com.quantbackengine.backend.service.python.PythonMarketDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for loading market data.
 * Uses Python scripts as the primary data source via {@link PythonMarketDataProvider}.
 */
@Service
@Slf4j
public class MarketDataService {

    private static final ZoneId ZONE_ID = ZoneId.of("UTC");
    private static final String DEFAULT_SOURCE = "yfinance";

    private final PythonMarketDataProvider marketDataProvider;
    private final PythonBridgeService bridgeService;

    public MarketDataService(PythonMarketDataProvider marketDataProvider,
                             PythonBridgeService bridgeService) {
        this.marketDataProvider = marketDataProvider;
        this.bridgeService = bridgeService;
    }

    /**
     * Load historical bar data for a symbol.
     * Uses Python bridge as the primary data source.
     */
    public BarSeries getBarSeries(String symbol, LocalDate start, LocalDate end) {
        String sanitizedSymbol = symbol.toUpperCase().replaceAll("[^A-Z0-9\\-]", "");

        if (!bridgeService.isAvailable()) {
            log.warn("Python bridge unavailable — falling back to classpath CSV for {}", sanitizedSymbol);
            return loadFromClasspath(sanitizedSymbol, start, end);
        }

        try {
            List<OhlcvBar> bars = marketDataProvider.fetchHistorical(sanitizedSymbol, start, end, DEFAULT_SOURCE);
            if (bars == null || bars.isEmpty()) {
                log.warn("Python bridge returned no data for {} — falling back to classpath CSV", sanitizedSymbol);
                return loadFromClasspath(sanitizedSymbol, start, end);
            }
            return toBarSeries(sanitizedSymbol, bars);
        } catch (Exception e) {
            log.error("Error fetching market data for {}: {} — falling back to classpath CSV", sanitizedSymbol, e.getMessage());
            return loadFromClasspath(sanitizedSymbol, start, end);
        }
    }

    private BarSeries loadFromClasspath(String symbol, LocalDate start, LocalDate end) {
        String resourcePath = "data/" + symbol + ".csv";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("No classpath CSV found for {}", symbol);
                return new BaseBarSeries(symbol);
            }
            List<Bar> bars = new ArrayList<>();
            boolean headerFound = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("date,")) {
                        headerFound = true;
                        continue;
                    }
                    if (!headerFound) continue;
                    String[] parts = line.split(",");
                    if (parts.length < 6) continue;
                    LocalDate date = LocalDate.parse(parts[0].trim());
                    if (date.isBefore(start) || date.isAfter(end)) continue;
                    bars.add(new BaseBar(
                            Duration.ofDays(1),
                            date.plusDays(1).atStartOfDay(ZONE_ID),
                            parts[1].trim(), parts[2].trim(),
                            parts[3].trim(), parts[4].trim(),
                            parts[5].trim()
                    ));
                }
            }
            log.info("Loaded {} bars for {} from classpath CSV", bars.size(), symbol);
            return new BaseBarSeries(symbol, bars);
        } catch (Exception e) {
            log.warn("Could not load classpath CSV for {}: {}", symbol, e.getMessage());
            return new BaseBarSeries(symbol);
        }
    }

    /**
     * Convert a list of {@link OhlcvBar} to a ta4j {@link BarSeries}.
     */
    private BarSeries toBarSeries(String symbol, List<OhlcvBar> ohlcvBars) {
        if (ohlcvBars == null || ohlcvBars.isEmpty()) {
            return new BaseBarSeries(symbol);
        }

        List<Bar> bars = new ArrayList<>(ohlcvBars.size());
        for (OhlcvBar ob : ohlcvBars) {
            bars.add(new BaseBar(
                    Duration.ofDays(1),
                    Instant.ofEpochMilli(ob.timestamp()).atZone(ZONE_ID),
                    String.valueOf(ob.open()),
                    String.valueOf(ob.high()),
                    String.valueOf(ob.low()),
                    String.valueOf(ob.close()),
                    String.valueOf(ob.volume())
            ));
        }

        log.info("Loaded {} bars for {} via Python bridge", bars.size(), symbol);
        return new BaseBarSeries(symbol, bars);
    }

    /**
     * Get list of available symbols.
     * Returns a default set since Python sources are dynamic.
     */
    public List<String> getAvailableSymbols() {
        return List.of("AAPL", "MSFT", "TSLA", "GOOGL", "AMZN",
                       "NVDA", "META", "SPY", "QQQ", "BTC-USD");
    }
}
