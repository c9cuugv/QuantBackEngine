package com.quantbackengine.quantbackengine.backtest;

import com.quantbackengine.quantbackengine.data.CsvDataProvider;
import com.quantbackengine.quantbackengine.strategy.MovingAverageCrossoverStrategy;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BacktesterTest {

    @Test
    void testBacktestProducesValidEquityCurve() throws Exception {
        var provider = new CsvDataProvider("data/test_prices.csv");
        BarSeries series = provider.getHistoricalData("TEST",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 10));

        var strategy = new MovingAverageCrossoverStrategy(2, 5);
        var backtester = new Backtester(10_000.0, 0.0);

        BacktestResult result = backtester.run(series, strategy);

        assertFalse(result.equityCurve().isEmpty());
        assertEquals(series.getBarCount(), result.equityCurve().size());
        assertTrue(result.equityCurve().getLast().portfolioValue() > 0);
    }
}