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
            log.warn("Python bridge unavailable — returning empty BarSeries for {}", sanitizedSymbol);
            return new BaseBarSeries(sanitizedSymbol);
        }

        try {
            List<OhlcvBar> bars = marketDataProvider.fetchHistorical(sanitizedSymbol, start, end, DEFAULT_SOURCE);
            return toBarSeries(sanitizedSymbol, bars);
        } catch (Exception e) {
            log.error("Error fetching market data for {}: {}", sanitizedSymbol, e.getMessage());
            return new BaseBarSeries(sanitizedSymbol);
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
