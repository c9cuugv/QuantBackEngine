package com.quantbackengine.quantbackengine.backtest;

import org.ta4j.core.TradingRecord;

import java.util.List;

/**
 * Container for backtest results.
 */
public record BacktestResult(
        TradingRecord tradingRecord,
        List<Backtester.Trade> completedTrades,
        List<Backtester.EquityPoint> equityCurve,
        double initialCapital) {}