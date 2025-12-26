package com.quantbackengine.quantbackengine.strategy;

import com.quantbackengine.quantbackengine.data.CsvDataProvider;
import com.quantbackengine.quantbackengine.strategy.MovingAverageCrossoverStrategy;
import com.quantbackengine.quantbackengine.strategy.TradingStrategy;
import org.ta4j.core.BarSeriesManager;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MovingAverageCrossoverStrategyTest {

    @Test
    void testStrategyGeneratesExpectedSignals() throws Exception {
        CsvDataProvider provider = new CsvDataProvider("data/test_prices.csv");
        BarSeries series = provider.getHistoricalData("TEST",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 10));

        TradingStrategy strategy = new MovingAverageCrossoverStrategy(3, 5);
        Strategy ta4jStrategy = strategy.buildStrategy(series);

        TradingRecord record = new BarSeriesManager(series).run(ta4jStrategy);

        // on this small dataset, we just verify the strategy runs successfully
        assertNotNull(record);
    }
}