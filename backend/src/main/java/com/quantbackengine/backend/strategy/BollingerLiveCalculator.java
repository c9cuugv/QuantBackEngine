package com.quantbackengine.backend.strategy;

/**
 * Zero-allocation Bollinger Bands + ADX calculator for live execution.
 * Uses primitive double[] ring buffers — no ta4j objects, no GC pressure.
 *
 * Usage per tick:
 *   calc.update(high, low, close);
 *   if (calc.isEntrySignal(prevClose, close)) { ... }
 */
public class BollingerLiveCalculator {

    private final int period;
    private final double k;
    private final int adxPeriod;

    // Ring buffer for Bollinger calculation
    private final double[] closes;
    private int head = 0;
    private int count = 0;

    // Wilder-smoothed ADX state
    private double smoothedTR = Double.NaN;
    private double smoothedPlusDM = Double.NaN;
    private double smoothedMinusDM = Double.NaN;
    private int diInitCount = 0;

    private double adxValue = Double.NaN;
    private double adxAccum = 0;
    private int dxCount = 0;

    // Previous bar values for DM/TR calculation
    private double prevClose = Double.NaN;
    private double prevHigh = Double.NaN;
    private double prevLow = Double.NaN;

    // Outputs
    private double upper = Double.NaN;
    private double lower = Double.NaN;
    private double middle = Double.NaN;

    public BollingerLiveCalculator(int period, double k, int adxPeriod) {
        this.period = period;
        this.k = k;
        this.adxPeriod = adxPeriod;
        this.closes = new double[period];
    }

    public void update(double high, double low, double close) {
        closes[head] = close;
        head = (head + 1) % period;
        if (count < period) count++;

        computeBollinger();
        computeADX(high, low, close);

        prevHigh = high;
        prevLow = low;
        prevClose = close;
    }

    private void computeBollinger() {
        if (count < period) {
            upper = lower = middle = Double.NaN;
            return;
        }
        double sum = 0;
        for (double c : closes) sum += c;
        double mean = sum / period;

        double variance = 0;
        for (double c : closes) variance += (c - mean) * (c - mean);
        double stddev = Math.sqrt(variance / period);

        middle = mean;
        upper = mean + k * stddev;
        lower = mean - k * stddev;
    }

    private void computeADX(double high, double low, double close) {
        if (Double.isNaN(prevClose)) return;

        double tr = Math.max(high - low,
                Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        double plusDM = (high - prevHigh) > (prevLow - low) && (high - prevHigh) > 0
                ? high - prevHigh : 0;
        double minusDM = (prevLow - low) > (high - prevHigh) && (prevLow - low) > 0
                ? prevLow - low : 0;

        // Seed Wilder smoothing with sum of first adxPeriod values
        if (diInitCount < adxPeriod) {
            diInitCount++;
            smoothedTR = Double.isNaN(smoothedTR) ? tr : smoothedTR + tr;
            smoothedPlusDM = Double.isNaN(smoothedPlusDM) ? plusDM : smoothedPlusDM + plusDM;
            smoothedMinusDM = Double.isNaN(smoothedMinusDM) ? minusDM : smoothedMinusDM + minusDM;
            if (diInitCount < adxPeriod) return;
        } else {
            smoothedTR = smoothedTR - smoothedTR / adxPeriod + tr;
            smoothedPlusDM = smoothedPlusDM - smoothedPlusDM / adxPeriod + plusDM;
            smoothedMinusDM = smoothedMinusDM - smoothedMinusDM / adxPeriod + minusDM;
        }

        if (smoothedTR == 0) return;

        double plusDI = 100.0 * smoothedPlusDM / smoothedTR;
        double minusDI = 100.0 * smoothedMinusDM / smoothedTR;
        double diSum = plusDI + minusDI;
        double dx = diSum == 0 ? 0 : 100.0 * Math.abs(plusDI - minusDI) / diSum;

        // Seed ADX with average of first adxPeriod DX values, then Wilder-smooth
        dxCount++;
        if (dxCount < adxPeriod) {
            adxAccum += dx;
        } else if (dxCount == adxPeriod) {
            adxAccum += dx;
            adxValue = adxAccum / adxPeriod;
        } else {
            adxValue = (adxValue * (adxPeriod - 1) + dx) / adxPeriod;
        }
    }

    /** Entry: close crossed up through lower band AND ADX < 25 (ranging regime). */
    public boolean isEntrySignal(double prevClose, double currentClose) {
        return !Double.isNaN(lower) && !Double.isNaN(adxValue)
                && prevClose < lower && currentClose >= lower
                && adxValue < 25.0;
    }

    /** Exit: close crossed down through upper band. */
    public boolean isExitSignal(double prevClose, double currentClose) {
        return !Double.isNaN(upper)
                && prevClose > upper && currentClose <= upper;
    }

    public double getUpper()  { return upper; }
    public double getLower()  { return lower; }
    public double getMiddle() { return middle; }
    public double getAdx()    { return adxValue; }
    public boolean isReady()  { return !Double.isNaN(adxValue) && !Double.isNaN(lower); }
}
