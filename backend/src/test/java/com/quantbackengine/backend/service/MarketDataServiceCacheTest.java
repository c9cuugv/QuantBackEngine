package com.quantbackengine.backend.service;

import com.quantbackengine.backend.dto.OhlcvBar;
import com.quantbackengine.backend.exception.MarketDataUnavailableException;
import com.quantbackengine.backend.repository.MarketDataRepository;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import com.quantbackengine.backend.service.python.PythonMarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DB-cache behaviour of {@link MarketDataService}: bridge fetch on miss,
 * Postgres/H2 serve on hit, no duplicate rows on overlap, offline serve
 * from cache, 503-style failure with cached-symbol listing on full miss.
 */
@DataJpaTest
class MarketDataServiceCacheTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final LocalDate END = LocalDate.of(2024, 1, 10);

    @Autowired
    private MarketDataRepository repository;

    private PythonBridgeService bridge;
    private PythonMarketDataProvider provider;
    private MarketDataService service;

    @BeforeEach
    void setUp() {
        bridge = mock(PythonBridgeService.class);
        provider = mock(PythonMarketDataProvider.class);
        service = new MarketDataService(provider, bridge, repository);
    }

    private static List<OhlcvBar> dailyBars(String symbol, LocalDate from, LocalDate to) {
        List<OhlcvBar> bars = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long ts = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            bars.add(new OhlcvBar(symbol, ts, 100, 110, 90, 105, 1_000_000));
        }
        return bars;
    }

    @Test
    void firstFetchPersistsAndReturnsBars() {
        when(bridge.isAvailable()).thenReturn(true);
        when(provider.fetchHistorical(eq("AAPL"), any(), any(), anyString()))
                .thenReturn(dailyBars("AAPL", START, END));

        BarSeries series = service.getBarSeries("AAPL", START, END);

        assertEquals(10, series.getBarCount());
        assertEquals(10, repository.count());
    }

    @Test
    void repeatCallServesFromDbWithoutSecondBridgeFetch() {
        when(bridge.isAvailable()).thenReturn(true);
        when(provider.fetchHistorical(eq("AAPL"), any(), any(), anyString()))
                .thenReturn(dailyBars("AAPL", START, END));

        service.getBarSeries("AAPL", START, END);
        BarSeries second = service.getBarSeries("AAPL", START, END);

        assertEquals(10, second.getBarCount());
        verify(provider, times(1)).fetchHistorical(anyString(), any(), any(), anyString());
    }

    @Test
    void overlappingFetchDoesNotDuplicateRows() {
        when(bridge.isAvailable()).thenReturn(true);
        when(provider.fetchHistorical(eq("AAPL"), any(), any(), anyString()))
                .thenReturn(dailyBars("AAPL", START, END));

        service.getBarSeries("AAPL", START, END.minusDays(5));
        service.getBarSeries("AAPL", START, END);

        assertEquals(10, repository.count());
    }

    @Test
    void bridgeDownWithCachedRangeServesCache() {
        when(bridge.isAvailable()).thenReturn(true);
        when(provider.fetchHistorical(eq("AAPL"), any(), any(), anyString()))
                .thenReturn(dailyBars("AAPL", START, END));
        service.getBarSeries("AAPL", START, END);

        when(bridge.isAvailable()).thenReturn(false);
        BarSeries offline = service.getBarSeries("AAPL", START, END);

        assertEquals(10, offline.getBarCount());
        verify(provider, times(1)).fetchHistorical(anyString(), any(), any(), anyString());
    }

    @Test
    void bridgeDownWithoutCacheThrowsListingCachedSymbols() {
        when(bridge.isAvailable()).thenReturn(true);
        when(provider.fetchHistorical(eq("MSFT"), any(), any(), anyString()))
                .thenReturn(dailyBars("MSFT", START, END));
        service.getBarSeries("MSFT", START, END);

        when(bridge.isAvailable()).thenReturn(false);
        MarketDataUnavailableException ex = assertThrows(
                MarketDataUnavailableException.class,
                () -> service.getBarSeries("AAPL", START, END));

        assertTrue(ex.getCachedSymbols().contains("MSFT"));
    }

    @Test
    void epochSecondTimestampsAreNormalizedOnPersistAndCacheHits() {
        // Real yfinance script emits epoch SECONDS (int(index.timestamp())),
        // not the millis the OhlcvBar contract claims. Service must normalize.
        List<OhlcvBar> secondBars = new ArrayList<>();
        for (LocalDate d = START; !d.isAfter(END); d = d.plusDays(1)) {
            long tsSeconds = d.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            secondBars.add(new OhlcvBar("AAPL", tsSeconds, 100, 110, 90, 105, 1_000_000));
        }
        when(bridge.isAvailable()).thenReturn(true);
        when(provider.fetchHistorical(eq("AAPL"), any(), any(), anyString()))
                .thenReturn(secondBars);

        service.getBarSeries("AAPL", START, END);

        // Rows must land on real 2024 dates, not 1970
        assertEquals(START.atStartOfDay(),
                repository.findAll().get(0).getTimestamp());

        // And the cache must actually hit on the second call
        service.getBarSeries("AAPL", START, END);
        verify(provider, times(1)).fetchHistorical(anyString(), any(), any(), anyString());
    }

    @Test
    void availableSymbolsIncludeCachedSymbols() {
        when(bridge.isAvailable()).thenReturn(true);
        when(provider.fetchHistorical(eq("ZZTOP"), any(), any(), anyString()))
                .thenReturn(dailyBars("ZZTOP", START, END));
        service.getBarSeries("ZZTOP", START, END);

        assertTrue(service.getAvailableSymbols().contains("ZZTOP"));
    }
}
