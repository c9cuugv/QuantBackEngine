package com.quantbackengine.quantbackengine.backtest;

import com.quantbackengine.quantbackengine.strategy.TradingStrategy;
import org.ta4j.core.*;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core backtesting engine.
 * Simulates trading by executing a strategy on historical data.
 */
public class Backtester {

    private final double initialCapital;
    private final double commissionRate; // e.g., 0.001 = 0.1%

    /**
     * Constructor.
     *
     * @param initialCapital starting cash amount
     * @param commissionRate fixed commission rate per trade (0 for none)
     */
    public Backtester(double initialCapital, double commissionRate) {
        if (initialCapital <= 0) {
            throw new IllegalArgumentException("Initial capital must be positive");
        }
        this.initialCapital = initialCapital;
        this.commissionRate = Math.max(0, commissionRate);
    }

    /**
     * Runs the backtest.
     *
     * @param series   the historical BarSeries
     * @param strategy the trading strategy
     * @return BacktestResult containing trades and equity curve
     */
    public BacktestResult run(BarSeries series, TradingStrategy strategy) {
        if (series == null || series.isEmpty()) {
            throw new IllegalArgumentException("BarSeries cannot be null or empty");
        }

        Strategy ta4jStrategy = strategy.buildStrategy(series);
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(ta4jStrategy);

        // Reconstruct portfolio equity curve and apply commission
        List<EquityPoint> equityCurve = new ArrayList<>();
        List<Trade> completedTrades = new ArrayList<>();

        Num cash = series.numOf(initialCapital);
        Num sharesHeld = series.numOf(0);
        Num zero = series.numOf(0);
        Num commRate = series.numOf(commissionRate);

        // Map entry/exit indices to positions for easy lookup
        Map<Integer, Position> entries = new HashMap<>();
        Map<Integer, Position> exits = new HashMap<>();

        for (Position p : tradingRecord.getPositions()) {
            entries.put(p.getEntry().getIndex(), p);
            exits.put(p.getExit().getIndex(), p);
        }
        Position currentPos = tradingRecord.getCurrentPosition();
        if (currentPos.isOpened()) {
            entries.put(currentPos.getEntry().getIndex(), currentPos);
        }

        int barCount = series.getBarCount();

        for (int i = 0; i < barCount; i++) {
            Bar bar = series.getBar(i);
            Num price = bar.getClosePrice();

            // Check for Entry
            if (entries.containsKey(i)) {
                Num amount = cash;
                Num commission = amount.multipliedBy(commRate);
                amount = amount.minus(commission);

                Num sharesToBuy = amount.dividedBy(price);
                sharesHeld = sharesHeld.plus(sharesToBuy);
                cash = cash.minus(amount).minus(commission);
            }

            // Check for Exit
            if (exits.containsKey(i)) {
                Position pos = exits.get(i);
                Num proceeds = sharesHeld.multipliedBy(price);
                Num commission = proceeds.multipliedBy(commRate);
                proceeds = proceeds.minus(commission);

                cash = cash.plus(proceeds);
                completedTrades.add(new Trade(series.getBar(pos.getEntry().getIndex()).getEndTime(),
                        pos.getEntry().getPricePerAsset(),
                        series.getBar(pos.getExit().getIndex()).getEndTime(),
                        pos.getExit().getPricePerAsset(),
                        sharesHeld,
                        commission.doubleValue()));

                sharesHeld = zero;
            }

            Num portfolioValue = cash.plus(sharesHeld.multipliedBy(price));
            equityCurve.add(new EquityPoint(bar.getEndTime(), portfolioValue.doubleValue()));
        }

        return new BacktestResult(tradingRecord, completedTrades, equityCurve, initialCapital);
    }

    /**
     * Simple record for a point on the equity curve.
     */
    public record EquityPoint(ZonedDateTime dateTime, double portfolioValue) {
    }

    /**
     * Simple record for a completed trade.
     */
    public record Trade(ZonedDateTime entryDate,
            Num entryPrice,
            ZonedDateTime exitDate,
            Num exitPrice,
            Num shares,
            double commission) {
    }
}