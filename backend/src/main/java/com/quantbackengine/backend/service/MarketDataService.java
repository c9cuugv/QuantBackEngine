package com.quantbackengine.backend.service;

import com.quantbackengine.backend.domain.MarketData;
import com.quantbackengine.backend.dto.OhlcvBar;
import com.quantbackengine.backend.exception.MarketDataUnavailableException;
import com.quantbackengine.backend.repository.MarketDataRepository;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import com.quantbackengine.backend.service.python.PythonMarketDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final MarketDataRepository repository;

    public MarketDataService(PythonMarketDataProvider marketDataProvider,
                             PythonBridgeService bridgeService,
                             MarketDataRepository repository) {
        this.marketDataProvider = marketDataProvider;
        this.bridgeService = bridgeService;
        this.repository = repository;
    }

    /** Calendar-day tolerance when judging whether cached bars cover a range (weekends/holidays). */
    private static final long COVERAGE_TOLERANCE_DAYS = 5;

    /**
     * Load historical bar data for a symbol.
     *
     * <p>DB cache first; on miss the Python bridge fetches and the bars are
     * upserted into the cache. Bridge down: cached bars are served if any
     * exist for the range, otherwise {@link MarketDataUnavailableException}.
     */
    public BarSeries getBarSeries(String symbol, LocalDate start, LocalDate end) {
        String sanitizedSymbol = symbol.toUpperCase().replaceAll("[^A-Z0-9\\-]", "");

        List<MarketData> cached = repository.findBySymbolAndTimestampBetweenOrderByTimestampAsc(
                sanitizedSymbol, start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        if (coversRange(cached, start, end)) {
            log.info("Serving {} bars for {} from DB cache", cached.size(), sanitizedSymbol);
            return toBarSeriesFromEntities(sanitizedSymbol, cached);
        }

        if (!bridgeService.isAvailable()) {
            if (!cached.isEmpty()) {
                log.warn("Bridge down — serving partial cache ({} bars) for {}", cached.size(), sanitizedSymbol);
                return toBarSeriesFromEntities(sanitizedSymbol, cached);
            }
            throw new MarketDataUnavailableException(sanitizedSymbol, repository.findDistinctSymbols());
        }

        List<OhlcvBar> bars = marketDataProvider.fetchHistorical(sanitizedSymbol, start, end, DEFAULT_SOURCE);
        if (bars == null || bars.isEmpty()) {
            if (!cached.isEmpty()) {
                return toBarSeriesFromEntities(sanitizedSymbol, cached);
            }
            throw new IllegalStateException("No market data available for " + sanitizedSymbol);
        }

        persistNewBars(sanitizedSymbol, bars, cached);
        return toBarSeries(sanitizedSymbol, bars);
    }

    private boolean coversRange(List<MarketData> cached, LocalDate start, LocalDate end) {
        if (cached.isEmpty()) {
            return false;
        }
        LocalDate effectiveEnd = end.isAfter(LocalDate.now()) ? LocalDate.now() : end;
        LocalDate first = cached.get(0).getTimestamp().toLocalDate();
        LocalDate last = cached.get(cached.size() - 1).getTimestamp().toLocalDate();
        return !first.isAfter(start.plusDays(COVERAGE_TOLERANCE_DAYS))
                && !last.isBefore(effectiveEnd.minusDays(COVERAGE_TOLERANCE_DAYS));
    }

    /**
     * Python scripts emit epoch seconds; the {@link OhlcvBar} contract says millis.
     * Values below 1e11 (~year 5138 in seconds, ~1973 in millis) are seconds.
     */
    private static Instant toInstant(long timestamp) {
        return timestamp < 100_000_000_000L
                ? Instant.ofEpochSecond(timestamp)
                : Instant.ofEpochMilli(timestamp);
    }

    private void persistNewBars(String symbol, List<OhlcvBar> bars, List<MarketData> cached) {
        Set<LocalDateTime> existing = new HashSet<>();
        for (MarketData m : cached) {
            existing.add(m.getTimestamp());
        }
        List<MarketData> toSave = new ArrayList<>();
        for (OhlcvBar b : bars) {
            LocalDateTime ts = LocalDateTime.ofInstant(toInstant(b.timestamp()), ZONE_ID);
            if (existing.add(ts)) {
                toSave.add(MarketData.builder()
                        .symbol(symbol)
                        .timestamp(ts)
                        .open(BigDecimal.valueOf(b.open()))
                        .high(BigDecimal.valueOf(b.high()))
                        .low(BigDecimal.valueOf(b.low()))
                        .close(BigDecimal.valueOf(b.close()))
                        .volume(b.volume())
                        .build());
            }
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
            log.info("Cached {} new bars for {}", toSave.size(), symbol);
        }
    }

    private BarSeries toBarSeriesFromEntities(String symbol, List<MarketData> entities) {
        List<Bar> bars = new ArrayList<>(entities.size());
        for (MarketData m : entities) {
            bars.add(new BaseBar(
                    Duration.ofDays(1),
                    m.getTimestamp().atZone(ZONE_ID),
                    m.getOpen().toPlainString(),
                    m.getHigh().toPlainString(),
                    m.getLow().toPlainString(),
                    m.getClose().toPlainString(),
                    String.valueOf(m.getVolume())
            ));
        }
        return new BaseBarSeries(symbol, bars);
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
                    toInstant(ob.timestamp()).atZone(ZONE_ID),
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
     * Get list of available symbols: defaults plus everything cached in the DB.
     */
    public List<String> getAvailableSymbols() {
        Set<String> symbols = new LinkedHashSet<>(List.of(
                "AAPL", "MSFT", "TSLA", "GOOGL", "AMZN",
                "NVDA", "META", "SPY", "QQQ", "BTC-USD"));
        symbols.addAll(repository.findDistinctSymbols());
        return new ArrayList<>(symbols);
    }
}
