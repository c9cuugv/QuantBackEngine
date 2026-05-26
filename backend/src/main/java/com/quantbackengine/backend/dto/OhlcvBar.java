package com.quantbackengine.backend.dto;

/**
 * Single OHLCV data point returned by a Python market data script.
 *
 * <p>Invariant: {@code high >= max(open, close)}, {@code low <= min(open, close)},
 * {@code volume >= 0}.
 *
 * @param symbol    ticker symbol
 * @param timestamp epoch milliseconds (UTC)
 * @param open      opening price
 * @param high      session high
 * @param low       session low
 * @param close     closing price
 * @param volume    traded volume (non-negative)
 */
public record OhlcvBar(
        String symbol,
        long timestamp,
        double open,
        double high,
        double low,
        double close,
        long volume
) {

    /**
     * Returns {@code true} when this bar satisfies the OHLCV invariant.
     */
    public boolean isValid() {
        return high >= Math.max(open, close)
                && low <= Math.min(open, close)
                && volume >= 0;
    }
}
