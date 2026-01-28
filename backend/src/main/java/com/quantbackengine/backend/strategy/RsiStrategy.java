package com.quantbackengine.backend.strategy;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

import java.util.List;
import java.util.Map;

/**
 * RSI (Relative Strength Index) Strategy.
 * Buy when RSI crosses above oversold level.
 * Sell when RSI crosses below overbought level.
 */
@Component
public class RsiStrategy implements TradingStrategy {

    public static final String ID = "RSI";
    private static final int DEFAULT_PERIOD = 14;
    private static final int DEFAULT_OVERSOLD = 30;
    private static final int DEFAULT_OVERBOUGHT = 70;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "RSI Momentum";
    }

    @Override
    public String getDescription() {
        return "A mean-reversion strategy that buys when RSI indicates oversold conditions " +
                "and sells when RSI indicates overbought conditions.";
    }

    @Override
    public Strategy buildStrategy(BarSeries series, Map<String, Object> parameters) {
        int period = getIntParam(parameters, "period", DEFAULT_PERIOD);
        int oversold = getIntParam(parameters, "oversoldThreshold", DEFAULT_OVERSOLD);
        int overbought = getIntParam(parameters, "overboughtThreshold", DEFAULT_OVERBOUGHT);

        if (oversold >= overbought) {
            throw new IllegalArgumentException("Oversold threshold must be less than overbought threshold");
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, period);

        // Buy when RSI crosses up through oversold level
        Rule entryRule = new CrossedUpIndicatorRule(rsi, oversold);
        // Sell when RSI crosses down through overbought level
        Rule exitRule = new CrossedDownIndicatorRule(rsi, overbought);

        return new BaseStrategy(getName(), entryRule, exitRule);
    }

    @Override
    public List<ParameterDefinition> getParameterDefinitions() {
        return List.of(
                new ParameterDefinition("period", "INTEGER", DEFAULT_PERIOD, 5, 50,
                        "RSI calculation period"),
                new ParameterDefinition("oversoldThreshold", "INTEGER", DEFAULT_OVERSOLD, 10, 40,
                        "RSI level considered oversold (buy signal)"),
                new ParameterDefinition("overboughtThreshold", "INTEGER", DEFAULT_OVERBOUGHT, 60, 90,
                        "RSI level considered overbought (sell signal)"));
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
