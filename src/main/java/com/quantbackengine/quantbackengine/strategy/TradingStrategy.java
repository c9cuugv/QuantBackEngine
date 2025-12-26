package com.quantbackengine.quantbackengine.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

/**
 * Interface for trading strategies that can be backtested.
 * Implementations should build a TA4J Strategy object for signal generation.
 */
public interface TradingStrategy {
    /**
     * Builds the TA4J strategy based on the provided bar series.
     *
     * @param series the historical bar series
     * @return a TA4J Strategy for generating buy/sell signals
     */
    Strategy buildStrategy(BarSeries series);

    /**
     * Optional: Executes the strategy on the series and returns the trading record.
     * This can be used for quick testing or integration.
     *
     * @param series the historical bar series
     * @return the TradingRecord from running the strategy
     */
    default TradingRecord execute(BarSeries series) {
        Strategy strategy = buildStrategy(series);
        return new org.ta4j.core.BarSeriesManager(series).run(strategy);
    }
}
