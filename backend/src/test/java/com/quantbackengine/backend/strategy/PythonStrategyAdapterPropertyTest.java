package com.quantbackengine.backend.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quantbackengine.backend.dto.BacktestResponse.MetricsDto;
import com.quantbackengine.backend.service.python.PythonBridgeService;
import net.jqwik.api.*;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test verifying that BacktestResponse metrics are always non-null and finite.
 *
 * <p><b>Property 6: BacktestResponse metrics are non-null</b><br>
 * <b>Validates: Requirement 4.5</b>
 */
class PythonStrategyAdapterPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * <b>Property 6: BacktestResponse metrics are non-null</b><br>
     * <b>Validates: Requirement 4.5</b>
     *
     * <p>For any JSON response with arbitrary subsets of metric fields present or absent,
     * all MetricsDto fields SHALL be non-null and finite (no NaN, no Infinity).
     */
    @Property(tries = 200)
    void metricsAreAlwaysFiniteRegardlessOfMissingFields(
            @ForAll("metricsJson") ObjectNode metricsNode) {

        PythonStrategyAdapter adapter = new PythonStrategyAdapter(
                "test_strat", "algo_trading/test.py",
                Mockito.mock(PythonBridgeService.class), MAPPER);

        MetricsDto metrics = adapter.mapMetrics(metricsNode);

        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalReturn()).isFinite();
        assertThat(metrics.getAnnualizedReturn()).isFinite();
        assertThat(metrics.getMaxDrawdown()).isFinite();
        assertThat(metrics.getMaxDrawdownPercent()).isFinite();
        assertThat(metrics.getSharpeRatio()).isFinite();
        assertThat(metrics.getBacktestYears()).isFinite();
        assertThat(metrics.getWinRate()).isFinite();

        assertThat(Double.isNaN(metrics.getTotalReturn())).isFalse();
        assertThat(Double.isNaN(metrics.getAnnualizedReturn())).isFalse();
        assertThat(Double.isNaN(metrics.getMaxDrawdown())).isFalse();
        assertThat(Double.isNaN(metrics.getMaxDrawdownPercent())).isFalse();
        assertThat(Double.isNaN(metrics.getSharpeRatio())).isFalse();
        assertThat(Double.isNaN(metrics.getBacktestYears())).isFalse();
        assertThat(Double.isNaN(metrics.getWinRate())).isFalse();

        assertThat(Double.isInfinite(metrics.getTotalReturn())).isFalse();
        assertThat(Double.isInfinite(metrics.getAnnualizedReturn())).isFalse();
        assertThat(Double.isInfinite(metrics.getMaxDrawdown())).isFalse();
        assertThat(Double.isInfinite(metrics.getMaxDrawdownPercent())).isFalse();
        assertThat(Double.isInfinite(metrics.getSharpeRatio())).isFalse();
        assertThat(Double.isInfinite(metrics.getBacktestYears())).isFalse();
        assertThat(Double.isInfinite(metrics.getWinRate())).isFalse();
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    /**
     * Generates ObjectNode instances with a random subset of metric fields present.
     * Some fields may be absent (testing default-to-0.0 behaviour).
     * Uses a bitmask integer to decide which fields are included, avoiding the 8-arg limit.
     */
    @Provide
    Arbitrary<ObjectNode> metricsJson() {
        Arbitrary<Double> dbl = Arbitraries.doubles()
                .between(-1000.0, 1000.0)
                .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d));
        Arbitrary<Integer> intVal = Arbitraries.integers().between(0, 10000);
        // 10-bit mask: each bit controls whether a field is present
        Arbitrary<Integer> mask = Arbitraries.integers().between(0, 1023);

        return Combinators.combine(dbl, dbl, dbl, dbl, dbl, dbl, intVal, intVal)
                .flatAs((totalReturn, annualizedReturn, maxDrawdown, maxDrawdownPct,
                         sharpe, years, totalTrades, winTrades) ->
                        Combinators.combine(intVal, dbl, mask)
                                .as((loseTrades, winRate, m) -> {
                                    ObjectNode node = MAPPER.createObjectNode();
                                    if ((m & 1) != 0)   node.put("total_return", totalReturn);
                                    if ((m & 2) != 0)   node.put("annualized_return", annualizedReturn);
                                    if ((m & 4) != 0)   node.put("max_drawdown", maxDrawdown);
                                    if ((m & 8) != 0)   node.put("max_drawdown_percent", maxDrawdownPct);
                                    if ((m & 16) != 0)  node.put("sharpe_ratio", sharpe);
                                    if ((m & 32) != 0)  node.put("backtest_years", years);
                                    if ((m & 64) != 0)  node.put("total_trades", totalTrades);
                                    if ((m & 128) != 0) node.put("winning_trades", winTrades);
                                    if ((m & 256) != 0) node.put("losing_trades", loseTrades);
                                    if ((m & 512) != 0) node.put("win_rate", winRate);
                                    return node;
                                })
                );
    }
}
