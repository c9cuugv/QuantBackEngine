package com.quantbackengine.backend.service.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quantbackengine.backend.dto.OhlcvBar;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test for the OHLCV invariant in DefaultPythonMarketDataProvider.
 *
 * <p><b>Property 3: OhlcvBar OHLCV invariant</b><br>
 * <b>Validates: Requirement 2.4</b>
 *
 * <p>For any {@link OhlcvBar} returned by {@code fetchHistorical()},
 * {@code high >= max(open, close)}, {@code low <= min(open, close)},
 * and {@code volume >= 0} SHALL hold.
 */
class PythonMarketDataProviderOhlcvInvariantPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Helper: build a JSON bar node from raw OHLCV values
    // -------------------------------------------------------------------------

    private ObjectNode buildBarNode(long timestamp, double open, double high, double low, double close, long volume) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("timestamp", timestamp);
        node.put("open",   open);
        node.put("high",   high);
        node.put("low",    low);
        node.put("close",  close);
        node.put("volume", volume);
        return node;
    }

    // -------------------------------------------------------------------------
    // Properties
    // -------------------------------------------------------------------------

    /**
     * <b>Property 3: OhlcvBar OHLCV invariant — all returned bars are valid</b><br>
     * <b>Validates: Requirement 2.4</b>
     *
     * <p>Given a JSON array that mixes valid and invalid bars,
     * every bar returned by {@code fetchHistorical()} must satisfy the invariant.
     */
    @Property(tries = 200)
    void fetchHistoricalOnlyReturnsValidBars(
            @ForAll("mixedOhlcvBars") List<OhlcvBarData> barDataList) {

        ArrayNode jsonArray = objectMapper.createArrayNode();
        for (OhlcvBarData d : barDataList) {
            jsonArray.add(buildBarNode(d.timestamp(), d.open(), d.high(), d.low(), d.close(), d.volume()));
        }

        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        when(mockBridge.invoke(anyString(), anyList())).thenReturn(jsonArray);
        when(mockBridge.isAvailable()).thenReturn(true);

        DefaultPythonMarketDataProvider provider = new DefaultPythonMarketDataProvider(mockBridge);

        List<OhlcvBar> bars = provider.fetchHistorical(
                "TEST", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "yfinance");

        assertNotNull(bars, "Result must not be null");

        for (OhlcvBar bar : bars) {
            assertTrue(
                    bar.high() >= Math.max(bar.open(), bar.close()),
                    String.format("high (%.4f) must be >= max(open=%.4f, close=%.4f)",
                            bar.high(), bar.open(), bar.close())
            );
            assertTrue(
                    bar.low() <= Math.min(bar.open(), bar.close()),
                    String.format("low (%.4f) must be <= min(open=%.4f, close=%.4f)",
                            bar.low(), bar.open(), bar.close())
            );
            assertTrue(
                    bar.volume() >= 0,
                    "volume must be >= 0, got: " + bar.volume()
            );
        }
    }

    /**
     * <b>Property 3: Bars violating the invariant are NOT in the result</b><br>
     * <b>Validates: Requirement 2.4</b>
     *
     * <p>For each bar in the input that violates the OHLCV invariant,
     * no bar with the same timestamp should appear in the output.
     */
    @Property(tries = 200)
    void fetchHistoricalExcludesInvalidBars(
            @ForAll("mixedOhlcvBars") List<OhlcvBarData> barDataList) {

        ArrayNode jsonArray = objectMapper.createArrayNode();
        for (OhlcvBarData d : barDataList) {
            jsonArray.add(buildBarNode(d.timestamp(), d.open(), d.high(), d.low(), d.close(), d.volume()));
        }

        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        when(mockBridge.invoke(anyString(), anyList())).thenReturn(jsonArray);
        when(mockBridge.isAvailable()).thenReturn(true);

        DefaultPythonMarketDataProvider provider = new DefaultPythonMarketDataProvider(mockBridge);

        List<OhlcvBar> bars = provider.fetchHistorical(
                "TEST", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "yfinance");

        // Collect timestamps of invalid input bars
        List<Long> invalidTimestamps = barDataList.stream()
                .filter(d -> !isValidOhlcv(d.open(), d.high(), d.low(), d.close(), d.volume()))
                .map(OhlcvBarData::timestamp)
                .toList();

        // None of those timestamps should appear in the result
        List<Long> resultTimestamps = bars.stream().map(OhlcvBar::timestamp).toList();
        for (long invalidTs : invalidTimestamps) {
            assertFalse(
                    resultTimestamps.contains(invalidTs),
                    "Bar with timestamp " + invalidTs + " violated the invariant and must not be in the result"
            );
        }
    }

    /**
     * <b>Property 3: All valid input bars are preserved in the result</b><br>
     * <b>Validates: Requirement 2.4</b>
     *
     * <p>Every bar in the input that satisfies the invariant must appear in the output
     * (no valid bars are accidentally dropped by the filter).
     */
    @Property(tries = 200)
    void fetchHistoricalPreservesAllValidBars(
            @ForAll("mixedOhlcvBars") List<OhlcvBarData> barDataList) {

        ArrayNode jsonArray = objectMapper.createArrayNode();
        for (OhlcvBarData d : barDataList) {
            jsonArray.add(buildBarNode(d.timestamp(), d.open(), d.high(), d.low(), d.close(), d.volume()));
        }

        PythonBridgeService mockBridge = mock(PythonBridgeService.class);
        when(mockBridge.invoke(anyString(), anyList())).thenReturn(jsonArray);
        when(mockBridge.isAvailable()).thenReturn(true);

        DefaultPythonMarketDataProvider provider = new DefaultPythonMarketDataProvider(mockBridge);

        List<OhlcvBar> bars = provider.fetchHistorical(
                "TEST", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "yfinance");

        // Collect timestamps of valid input bars (using unique timestamps to avoid ambiguity)
        List<Long> validTimestamps = barDataList.stream()
                .filter(d -> isValidOhlcv(d.open(), d.high(), d.low(), d.close(), d.volume()))
                .map(OhlcvBarData::timestamp)
                .distinct()
                .toList();

        List<Long> resultTimestamps = bars.stream().map(OhlcvBar::timestamp).toList();
        for (long validTs : validTimestamps) {
            assertTrue(
                    resultTimestamps.contains(validTs),
                    "Valid bar with timestamp " + validTs + " must be present in the result"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Generates a mixed list of valid and invalid OHLCV bar data.
     * Some bars satisfy the invariant, others deliberately violate it.
     */
    @Provide
    Arbitrary<List<OhlcvBarData>> mixedOhlcvBars() {
        Arbitrary<OhlcvBarData> validBar   = validOhlcvBar();
        Arbitrary<OhlcvBarData> invalidBar = invalidOhlcvBar();

        // Mix valid and invalid bars in a list of size 0–30
        return Arbitraries.oneOf(validBar, invalidBar)
                .list()
                .ofMinSize(0)
                .ofMaxSize(30)
                .uniqueElements(OhlcvBarData::timestamp);
    }

    /**
     * Generates a bar that satisfies the OHLCV invariant:
     * high >= max(open, close), low <= min(open, close), volume >= 0.
     */
    @Provide
    Arbitrary<OhlcvBarData> validOhlcvBar() {
        long minTs = 946684800000L;   // 2000-01-01
        long maxTs = 1893456000000L;  // 2030-01-01

        Arbitrary<Long> timestamp = Arbitraries.longs().between(minTs, maxTs);
        Arbitrary<Double> base    = Arbitraries.doubles().between(1.0, 10000.0);
        Arbitrary<Double> spread  = Arbitraries.doubles().between(0.0, 100.0);
        Arbitrary<Long>   volume  = Arbitraries.longs().between(0L, 1_000_000L);

        return Combinators.combine(timestamp, base, spread, volume)
                .as((ts, b, s, vol) -> {
                    double open  = b;
                    double close = b;
                    double high  = b + s;       // high >= max(open, close)
                    double low   = b - s;       // low  <= min(open, close)
                    return new OhlcvBarData(ts, open, high, low, close, vol);
                });
    }

    /**
     * Generates a bar that violates at least one part of the OHLCV invariant.
     */
    @Provide
    Arbitrary<OhlcvBarData> invalidOhlcvBar() {
        long minTs = 946684800000L;
        long maxTs = 1893456000000L;

        Arbitrary<Long>   timestamp = Arbitraries.longs().between(minTs, maxTs);
        Arbitrary<Double> price     = Arbitraries.doubles().between(1.0, 10000.0);
        Arbitrary<Long>   negVol    = Arbitraries.longs().between(Long.MIN_VALUE, -1L);

        // Three kinds of violations — pick one at random
        Arbitrary<OhlcvBarData> highTooLow = Combinators.combine(timestamp, price, price)
                .as((ts, base, violation) -> {
                    // high < open (violation)
                    double open  = base + 10.0;
                    double close = base;
                    double high  = base - 1.0;  // high < open → invalid
                    double low   = base - 2.0;
                    return new OhlcvBarData(ts, open, high, low, close, 100L);
                });

        Arbitrary<OhlcvBarData> lowTooHigh = Combinators.combine(timestamp, price, price)
                .as((ts, base, violation) -> {
                    // low > close (violation)
                    double open  = base;
                    double close = base;
                    double high  = base + 2.0;
                    double low   = base + 1.0;  // low > close → invalid
                    return new OhlcvBarData(ts, open, high, low, close, 100L);
                });

        Arbitrary<OhlcvBarData> negativeVolume = Combinators.combine(timestamp, price, negVol)
                .as((ts, base, vol) -> {
                    double open  = base;
                    double close = base;
                    double high  = base + 1.0;
                    double low   = base - 1.0;
                    return new OhlcvBarData(ts, open, high, low, close, vol);
                });

        return Arbitraries.oneOf(highTooLow, lowTooHigh, negativeVolume);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isValidOhlcv(double open, double high, double low, double close, long volume) {
        return high >= Math.max(open, close)
                && low <= Math.min(open, close)
                && volume >= 0;
    }

    /**
     * Simple value holder for generated OHLCV bar data.
     */
    record OhlcvBarData(long timestamp, double open, double high, double low, double close, long volume) {}
}
