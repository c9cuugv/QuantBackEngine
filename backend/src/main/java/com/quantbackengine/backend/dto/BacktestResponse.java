package com.quantbackengine.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for backtest results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResponse {

    private String id;
    private String symbol;
    private String strategy;
    private MetricsDto metrics;
    private List<TradeDto> trades;
    private List<EquityPointDto> equityCurve;
    private List<CandleDto> candles;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsDto {
        private double totalReturn;
        private double annualizedReturn;
        private double maxDrawdown;
        private double maxDrawdownPercent;
        private double sharpeRatio;
        private double backtestYears;
        private int totalTrades;
        private int winningTrades;
        private int losingTrades;
        private double winRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TradeDto {
        private String type; // BUY or SELL
        private LocalDateTime entryDate;
        private double entryPrice;
        private LocalDateTime exitDate;
        private double exitPrice;
        private double shares;
        private double pnl;
        private double commission;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquityPointDto {
        private long timestamp; // Unix timestamp in milliseconds
        private double value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandleDto {
        private long time; // Unix timestamp in seconds (for Lightweight Charts)
        private double open;
        private double high;
        private double low;
        private double close;
        private long volume;
    }
}
