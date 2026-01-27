package com.quantbackengine.backend.strategy;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

import java.util.List;
import java.util.Map;

/**
 * MACD (Moving Average Convergence Divergence) Strategy.
 * Buy when MACD line crosses above the signal line.
 * Sell when MACD line crosses below the signal line.
 */
@Component
public class MacdStrategy implements TradingStrategy {

    public static final String ID = "MACD";
    private static final int DEFAULT_SHORT_PERIOD = 12;
    private static final int DEFAULT_LONG_PERIOD = 26;
    private static final int DEFAULT_SIGNAL_PERIOD = 9;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "MACD Crossover";
    }

    @Override
    public String getDescription() {
        return "A trend-following momentum strategy that tracks the relationship between two " +
                "moving averages. Signals are generated when the MACD line crosses the signal line.";
    }

    @Override
    public Strategy buildStrategy(BarSeries series, Map<String, Object> parameters) {
        int shortPeriod = getIntParam(parameters, "shortPeriod", DEFAULT_SHORT_PERIOD);
        int longPeriod = getIntParam(parameters, "longPeriod", DEFAULT_LONG_PERIOD);
        int signalPeriod = getIntParam(parameters, "signalPeriod", DEFAULT_SIGNAL_PERIOD);

        if (shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("Short period must be less than long period");
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(close, shortPeriod, longPeriod);
        EMAIndicator signalLine = new EMAIndicator(macd, signalPeriod);

        // Buy when MACD crosses above signal line
        Rule entryRule = new CrossedUpIndicatorRule(macd, signalLine);
        // Sell when MACD crosses below signal line
        Rule exitRule = new CrossedDownIndicatorRule(macd, signalLine);

        return new BaseStrategy(getName(), entryRule, exitRule);
    }

    @Override
    public List<ParameterDefinition> getParameterDefinitions() {
        return List.of(
                new ParameterDefinition("shortPeriod", "INTEGER", DEFAULT_SHORT_PERIOD, 5, 20,
                        "Short-term EMA period (fast line)"),
                new ParameterDefinition("longPeriod", "INTEGER", DEFAULT_LONG_PERIOD, 15, 50,
                        "Long-term EMA period (slow line)"),
                new ParameterDefinition("signalPeriod", "INTEGER", DEFAULT_SIGNAL_PERIOD, 5, 20,
                        "Signal line EMA period"));
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
