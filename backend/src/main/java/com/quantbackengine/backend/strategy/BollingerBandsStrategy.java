package com.quantbackengine.backend.strategy;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

import java.util.List;
import java.util.Map;

/**
 * Bollinger Bands Mean-Reversion Strategy.
 * Buy when price crosses below the lower band (oversold).
 * Sell when price crosses above the upper band (overbought).
 */
@Component
public class BollingerBandsStrategy implements TradingStrategy {

    public static final String ID = "BOLLINGER";
    private static final int DEFAULT_PERIOD = 20;
    private static final double DEFAULT_K = 2.0;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return "Bollinger Bands";
    }

    @Override
    public String getDescription() {
        return "A mean-reversion strategy using Bollinger Bands. Buys when price touches " +
                "the lower band (oversold) and sells when price touches the upper band (overbought).";
    }

    @Override
    public Strategy buildStrategy(BarSeries series, Map<String, Object> parameters) {
        int period = getIntParam(parameters, "period", DEFAULT_PERIOD);
        double k = getDoubleParam(parameters, "standardDeviations", DEFAULT_K);

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(close, period);
        StandardDeviationIndicator stdDev = new StandardDeviationIndicator(close, period);

        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(sma);
        BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, stdDev, series.numOf(k));
        BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, stdDev, series.numOf(k));

        // Buy when price crosses above lower band (coming from below)
        Rule entryRule = new CrossedUpIndicatorRule(close, lower);
        // Sell when price crosses below upper band (coming from above)
        Rule exitRule = new CrossedDownIndicatorRule(close, upper);

        return new BaseStrategy(getName(), entryRule, exitRule);
    }

    @Override
    public List<ParameterDefinition> getParameterDefinitions() {
        return List.of(
                new ParameterDefinition("period", "INTEGER", DEFAULT_PERIOD, 10, 50,
                        "Period for SMA and standard deviation calculation"),
                new ParameterDefinition("standardDeviations", "DECIMAL", DEFAULT_K, 1.0, 3.0,
                        "Number of standard deviations for bands"));
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

    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
