package com.quantbackengine.backend.strategy;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Strategy;
import org.ta4j.core.BaseBar;
import java.time.Duration;
import java.util.Map;

/**
 * Extension of TradingStrategy for strategies that operate on two correlated assets.
 * buildStrategy(single) is intentionally unsupported — use buildPairsStrategy.
 */
public interface PairsTradingStrategy extends TradingStrategy {

    /**
     * Build the strategy from two aligned bar series.
     * Implementations must also populate the spread series retrievable via getSpreadSeries().
     */
    Strategy buildPairsStrategy(BarSeries seriesA, BarSeries seriesB, Map<String, Object> parameters);

    /**
     * Returns the synthetic spread series constructed during the last buildPairsStrategy call.
     * BacktestService runs the BarSeriesManager on this series, not the raw asset series.
     */
    BarSeries getSpreadSeries();

    @Override
    default Strategy buildStrategy(BarSeries series, Map<String, Object> parameters) {
        throw new UnsupportedOperationException(
                "Pairs strategy '" + getId() + "' requires two series. Use buildPairsStrategy.");
    }

    /**
     * Builds a synthetic BarSeries where close = price_A - price_B.
     * Aligns on the shorter series by taking the tail of the longer one.
     * O = H = L = C = spread value; volume = 0.
     */
    default BarSeries buildSpreadSeries(BarSeries a, BarSeries b) {
        BaseBarSeries spread = new BaseBarSeriesBuilder()
                .withName("SPREAD_" + a.getName() + "_" + b.getName())
                .build();

        int limit   = Math.min(a.getBarCount(), b.getBarCount());
        int offsetA = a.getBarCount() - limit;
        int offsetB = b.getBarCount() - limit;

        for (int i = 0; i < limit; i++) {
            Bar barA = a.getBar(offsetA + i);
            Bar barB = b.getBar(offsetB + i);

            double spreadVal = barA.getClosePrice().doubleValue() - barB.getClosePrice().doubleValue();

            spread.addBar(new BaseBar(
                    Duration.ofDays(1),
                    barA.getEndTime(),
                    spreadVal, spreadVal, spreadVal, spreadVal,
                    0d
            ));
        }
        return spread;
    }
}
