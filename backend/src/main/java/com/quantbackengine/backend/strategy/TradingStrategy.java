package com.quantbackengine.backend.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

import java.util.Map;

/**
 * Interface for all backtestable trading strategies.
 * Same pattern from the original codebase, adapted for Spring.
 */
public interface TradingStrategy {

    /**
     * Unique identifier for this strategy.
     */
    String getId();

    /**
     * Human-readable name.
     */
    String getName();

    /**
     * Description of the strategy logic.
     */
    String getDescription();

    /**
     * Builds the TA4J strategy based on the provided series and parameters.
     *
     * @param series     the historical bar series
     * @param parameters user-provided parameters
     * @return a TA4J Strategy for generating buy/sell signals
     */
    Strategy buildStrategy(BarSeries series, Map<String, Object> parameters);

    /**
     * Returns metadata about configurable parameters.
     */
    java.util.List<ParameterDefinition> getParameterDefinitions();

    /**
     * Describes a configurable parameter.
     */
    record ParameterDefinition(
            String name,
            String type,
            Object defaultValue,
            Object minValue,
            Object maxValue,
            String description) {
    }
}
