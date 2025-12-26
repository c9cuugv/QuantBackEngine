package com.quantbackengine.quantbackengine.metrics;

import com.quantbackengine.quantbackengine.backtest.BacktestResult;
import com.quantbackengine.quantbackengine.backtest.Backtester;
import com.quantbackengine.quantbackengine.metrics.PerformanceMetrics;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceMetricsTest {

    @Test
    void testMetricsOnKnownEquityCurve() {
        java.time.ZonedDateTime start = java.time.ZonedDateTime.now();
        List<Backtester.EquityPoint> curve = List.of(
                new Backtester.EquityPoint(start, 10000),
                new Backtester.EquityPoint(start.plusDays(1), 12000),
                new Backtester.EquityPoint(start.plusDays(2), 15000),
                new Backtester.EquityPoint(start.plusDays(3), 11000),
                new Backtester.EquityPoint(start.plusDays(4), 13200));

        var result = new BacktestResult(null, List.of(), curve, 10000.0);
        var metricsCalc = new PerformanceMetrics();

        var report = metricsCalc.calculate(result);

        assertEquals(0.32, report.totalReturn(), 0.01);
        assertTrue(report.maxDrawdown().absolute() > 3999);
        assertTrue(report.maxDrawdown().percentage() > 0.26);
        assertTrue(report.sharpeRatio() != 0); // volatility exists
    }
}