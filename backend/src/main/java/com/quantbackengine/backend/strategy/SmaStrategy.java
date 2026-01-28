package com.quantbackengine.backend.strategy;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

import java.util.List;
import java.util.Map;

/**
 * Classic SMA Crossover Strategy.
 * Buy when short SMA crosses above long SMA.
 * Sell when short SMA crosses below long SMA.
 */
@Component
public class SmaStrategy implements TradingStrategy {

    public static final String ID = "SMA_CROSSOVER";
    private static final int DEFAULT_SHORT = 50;
    private static final int DEFAULT_LONG = 200;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "SMA Crossover";
    }

    @Override
    public String getDescription() {
        return "A trend-following strategy that buys when a short-term SMA crosses above a long-term SMA, " +
                "and sells when it crosses below.";
    }

    @Override
    public Strategy buildStrategy(BarSeries series, Map<String, Object> parameters) {
        int shortPeriod = getIntParam(parameters, "shortPeriod", DEFAULT_SHORT);
        int longPeriod = getIntParam(parameters, "longPeriod", DEFAULT_LONG);

        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("Short period must be less than long period");
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, shortPeriod);
        SMAIndicator longSma = new SMAIndicator(close, longPeriod);

        Rule entryRule = new CrossedUpIndicatorRule(shortSma, longSma);
        Rule exitRule = new CrossedDownIndicatorRule(shortSma, longSma);

        return new BaseStrategy(getName(), entryRule, exitRule);
    }

    @Override
    public List<ParameterDefinition> getParameterDefinitions() {
        return List.of(
                new ParameterDefinition("shortPeriod", "INTEGER", DEFAULT_SHORT, 5, 100,
                        "Short-term SMA period (e.g., 50)"),
                new ParameterDefinition("longPeriod", "INTEGER", DEFAULT_LONG, 50, 500,
                        "Long-term SMA period (e.g., 200)"));
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
