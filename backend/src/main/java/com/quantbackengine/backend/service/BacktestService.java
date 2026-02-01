package com.quantbackengine.backend.service;

import com.quantbackengine.backend.dto.BacktestRequest;
import com.quantbackengine.backend.dto.BacktestResponse;
import com.quantbackengine.backend.dto.BacktestResponse.*;
import com.quantbackengine.backend.strategy.StrategyRegistry;
import com.quantbackengine.backend.strategy.TradingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Core backtesting service.
 * Executes strategies on historical data and computes metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final StrategyRegistry strategyRegistry;
    private final MarketDataService marketDataService;

    @Value("${backtest.default.initial-capital:100000.0}")
    private double defaultInitialCapital;

    @Value("${backtest.default.commission-rate:0.001}")
    private double defaultCommissionRate;

    @Value("${backtest.default.risk-free-rate:0.02}")
    private double riskFreeRate;

    /**
     * Run a backtest based on the provided request.
     */
    public BacktestResponse runBacktest(BacktestRequest request) {
        log.info("Starting backtest for {} with strategy {}", request.getSymbol(), request.getStrategy());

        // Validate strategy
        TradingStrategy strategy = strategyRegistry.getStrategy(request.getStrategy())
                .orElseThrow(() -> new IllegalArgumentException("Unknown strategy: " + request.getStrategy()));

        // Load market data
        BarSeries series = marketDataService.getBarSeries(
                request.getSymbol(),
                request.getStartDate(),
                request.getEndDate());

        if (series.isEmpty()) {
            throw new IllegalStateException("No market data available for " + request.getSymbol());
        }

        log.info("Loaded {} bars for {}", series.getBarCount(), request.getSymbol());

        // Get config
        double initialCapital = request.getInitialCapital() != null
                ? request.getInitialCapital()
                : defaultInitialCapital;
        double commissionRate = request.getCommissionRate() != null
                ? request.getCommissionRate()
                : defaultCommissionRate;

        // Build & run strategy
        Strategy ta4jStrategy = strategy.buildStrategy(series, request.getParameters());
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(ta4jStrategy);

        // Process results
        int barCount = series.getBarCount();
        List<TradeDto> trades = new ArrayList<>(barCount);
        List<EquityPointDto> equityCurve = new ArrayList<>(barCount);
        List<CandleDto> candles = new ArrayList<>(barCount);

        Num cash = series.numOf(initialCapital);
        Num sharesHeld = series.numOf(0);
        Num zero = series.numOf(0);
        Num commRate = series.numOf(commissionRate);

        // Map positions
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

        int winCount = 0;
        int lossCount = 0;

        for (int i = 0; i < series.getBarCount(); i++) {
            Bar bar = series.getBar(i);
            Num price = bar.getClosePrice();

            // Record candle for charting
            candles.add(CandleDto.builder()
                    .time(bar.getEndTime().toEpochSecond())
                    .open(bar.getOpenPrice().doubleValue())
                    .high(bar.getHighPrice().doubleValue())
                    .low(bar.getLowPrice().doubleValue())
                    .close(bar.getClosePrice().doubleValue())
                    .volume(bar.getVolume().longValue())
                    .build());

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

                double entryVal = pos.getEntry().getPricePerAsset().doubleValue() * sharesHeld.doubleValue();
                double exitVal = proceeds.doubleValue();
                double pnl = exitVal - entryVal;

                if (pnl > 0)
                    winCount++;
                else
                    lossCount++;

                trades.add(TradeDto.builder()
                        .type("ROUND_TRIP")
                        .entryDate(series.getBar(pos.getEntry().getIndex()).getEndTime().toLocalDateTime())
                        .entryPrice(pos.getEntry().getPricePerAsset().doubleValue())
                        .exitDate(bar.getEndTime().toLocalDateTime())
                        .exitPrice(price.doubleValue())
                        .shares(sharesHeld.doubleValue())
                        .pnl(pnl)
                        .commission(commission.doubleValue())
                        .build());

                cash = cash.plus(proceeds);
                sharesHeld = zero;
            }

            Num portfolioValue = cash.plus(sharesHeld.multipliedBy(price));
            equityCurve.add(EquityPointDto.builder()
                    .timestamp(bar.getEndTime().toInstant().toEpochMilli())
                    .value(portfolioValue.doubleValue())
                    .build());
        }

        // Calculate metrics
        MetricsDto metrics = calculateMetrics(equityCurve, initialCapital, trades.size(), winCount, lossCount);

        log.info("Backtest complete. Total Return: {:.2f}%", metrics.getTotalReturn() * 100);

        return BacktestResponse.builder()
                .id(UUID.randomUUID().toString())
                .symbol(request.getSymbol())
                .strategy(request.getStrategy())
                .metrics(metrics)
                .trades(trades)
                .equityCurve(equityCurve)
                .candles(candles)
                .build();
    }

    private MetricsDto calculateMetrics(List<EquityPointDto> curve, double initial,
            int totalTrades, int wins, int losses) {
        if (curve.isEmpty()) {
            return MetricsDto.builder().build();
        }

        double finalValue = curve.get(curve.size() - 1).getValue();
        double totalReturn = (finalValue - initial) / initial;

        // Calculate years
        long startMs = curve.get(0).getTimestamp();
        long endMs = curve.get(curve.size() - 1).getTimestamp();
        double years = (endMs - startMs) / (365.25 * 24 * 60 * 60 * 1000.0);
        double annualizedReturn = years > 0 ? Math.pow(1 + totalReturn, 1 / years) - 1 : 0;

        // Max drawdown
        double peak = -Double.MAX_VALUE;
        double maxDd = 0.0;
        double maxDdPct = 0.0;

        for (EquityPointDto point : curve) {
            double value = point.getValue();
            if (value > peak) {
                peak = value;
            }
            double drawdown = peak - value;
            if (drawdown > maxDd) {
                maxDd = drawdown;
                maxDdPct = maxDd / peak;
            }
        }

        // Sharpe ratio
        double sharpe = 0.0;
        if (curve.size() > 1) {
            double[] returns = new double[curve.size() - 1];
            for (int i = 1; i < curve.size(); i++) {
                double prev = curve.get(i - 1).getValue();
                double curr = curve.get(i).getValue();
                returns[i - 1] = (curr - prev) / prev;
            }

            double mean = Arrays.stream(returns).average().orElse(0);
            double variance = Arrays.stream(returns).map(r -> Math.pow(r - mean, 2)).sum() / (returns.length - 1);
            double stdDev = Math.sqrt(variance);

            if (stdDev > 0) {
                double annualizedMean = mean * 252;
                double annualizedStdDev = stdDev * Math.sqrt(252);
                sharpe = (annualizedMean - riskFreeRate) / annualizedStdDev;
            }
        }

        double winRate = totalTrades > 0 ? (double) wins / totalTrades : 0;

        return MetricsDto.builder()
                .totalReturn(totalReturn)
                .annualizedReturn(annualizedReturn)
                .maxDrawdown(maxDd)
                .maxDrawdownPercent(maxDdPct)
                .sharpeRatio(sharpe)
                .backtestYears(years)
                .totalTrades(totalTrades)
                .winningTrades(wins)
                .losingTrades(losses)
                .winRate(winRate)
                .build();
    }
}
