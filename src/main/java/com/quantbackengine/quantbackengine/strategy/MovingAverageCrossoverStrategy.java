package com.quantbackengine.quantbackengine.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

/**
 * A simple moving average crossover strategy.
 * Buy when short SMA crosses up long SMA.
 * Sell when short SMA crosses down long SMA.
 */
public class MovingAverageCrossoverStrategy implements TradingStrategy {

    private final int shortWindow;
    private final int longWindow;

    public MovingAverageCrossoverStrategy(int shortWindow, int longWindow) {
        this.shortWindow = shortWindow;
        this.longWindow = longWindow;
    }

    @Override
    public Strategy buildStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, shortWindow);
        SMAIndicator longSma = new SMAIndicator(close, longWindow);

        Rule entryRule = new CrossedUpIndicatorRule(shortSma, longSma);
        Rule exitRule = new CrossedDownIndicatorRule(shortSma, longSma);

        return new BaseStrategy("SMA_Crossover", entryRule, exitRule);
    }
}
