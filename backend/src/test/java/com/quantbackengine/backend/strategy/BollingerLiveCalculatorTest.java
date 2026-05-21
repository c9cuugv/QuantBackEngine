package com.quantbackengine.backend.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BollingerLiveCalculatorTest {

    // Small periods for fast convergence: period=3, adxPeriod=2
    // isReady() becomes true after 4 bars:
    //   bar-1: prevClose=NaN → skip ADX
    //   bar-2: diInitCount 0→1 (<2) → accumulate, return
    //   bar-3: diInitCount 1→2 (==2) → compute DI; dxCount 0→1 (<2) → accumulate
    //   bar-4: Wilder smooth; compute DI; dxCount 1→2 (==2) → adxValue set
    private static final int PERIOD = 3;
    private static final double K = 2.0;
    private static final int ADX_PERIOD = 2;

    @Test
    void notReady_onConstruction() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        assertFalse(calc.isReady());
        assertTrue(Double.isNaN(calc.getLower()));
        assertTrue(Double.isNaN(calc.getUpper()));
        assertTrue(Double.isNaN(calc.getMiddle()));
        assertTrue(Double.isNaN(calc.getAdx()));
    }

    @Test
    void notReady_afterOnlyOneBar() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        calc.update(101, 99, 100);
        assertFalse(calc.isReady());
        assertTrue(Double.isNaN(calc.getAdx()));
    }

    @Test
    void ready_afterFourBars() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        feedConstantBars(calc, 4);
        assertTrue(calc.isReady());
        assertFalse(Double.isNaN(calc.getAdx()));
        assertFalse(Double.isNaN(calc.getLower()));
    }

    @Test
    void bollingerMiddle_equalsMovingAverage() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(3, K, ADX_PERIOD);
        calc.update(101, 99, 100);
        calc.update(102, 100, 101);
        calc.update(103, 101, 102);
        // middle = (100 + 101 + 102) / 3 = 101.0
        assertEquals(101.0, calc.getMiddle(), 0.001);
    }

    @Test
    void ringBuffer_wrapsCorrectly() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(3, K, ADX_PERIOD);
        calc.update(101, 99, 100);
        calc.update(102, 100, 101);
        calc.update(103, 101, 102);
        assertEquals(101.0, calc.getMiddle(), 0.001);
        // 4th bar: oldest (100) replaced by 103 → window=[101,102,103]
        calc.update(104, 102, 103);
        assertEquals(102.0, calc.getMiddle(), 0.001);
    }

    @Test
    void bollingerBands_constantPrices_bandWidthIsZero() {
        // With identical closes, stddev=0 → upper = lower = middle
        BollingerLiveCalculator calc = new BollingerLiveCalculator(3, K, ADX_PERIOD);
        for (int i = 0; i < 3; i++) {
            calc.update(101, 99, 100);
        }
        assertFalse(Double.isNaN(calc.getMiddle()));
        assertEquals(100.0, calc.getMiddle(), 0.001);
        assertEquals(calc.getMiddle(), calc.getUpper(), 0.001);
        assertEquals(calc.getMiddle(), calc.getLower(), 0.001);
    }

    @Test
    void entrySignal_returnsFalse_beforeReady() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        calc.update(101, 99, 100);
        // lower and adxValue are NaN → guard fires
        assertFalse(calc.isEntrySignal(99.0, 101.0));
    }

    @Test
    void exitSignal_returnsFalse_beforeBollingerComputed() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        calc.update(101, 99, 100);
        // upper is NaN → guard fires
        assertFalse(calc.isExitSignal(101.0, 99.0));
    }

    @Test
    void entrySignal_crossUpThroughLower_lowAdx_fires() {
        // Constant prices → ADX = 0 (no directional movement), ranging regime
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        feedConstantBars(calc, 4);

        assertTrue(calc.isReady());
        assertEquals(0.0, calc.getAdx(), 0.001); // no directional movement

        double lower = calc.getLower(); // = middle (stddev=0 with constant prices)
        assertTrue(calc.isEntrySignal(lower - 1.0, lower + 0.1),
                "cross-up through lower band with ADX=0 must fire entry");
    }

    @Test
    void entrySignal_prevAboveLower_noFire() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        feedConstantBars(calc, 4);
        double lower = calc.getLower();
        // prevClose already above lower — no cross from below
        assertFalse(calc.isEntrySignal(lower + 1.0, lower + 2.0));
    }

    @Test
    void entrySignal_currentBelowLower_noFire() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        feedConstantBars(calc, 4);
        double lower = calc.getLower();
        // currentClose doesn't reach lower band
        assertFalse(calc.isEntrySignal(lower - 2.0, lower - 0.5));
    }

    @Test
    void exitSignal_crossDownThroughUpper_fires() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        feedConstantBars(calc, 4);

        double upper = calc.getUpper();
        assertFalse(Double.isNaN(upper));
        assertTrue(calc.isExitSignal(upper + 1.0, upper - 0.1),
                "cross-down through upper band must fire exit");
    }

    @Test
    void exitSignal_prevBelowUpper_noFire() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        feedConstantBars(calc, 4);
        double upper = calc.getUpper();
        // prevClose never exceeded upper
        assertFalse(calc.isExitSignal(upper - 2.0, upper - 1.0));
    }

    @Test
    void exitSignal_prevAboveUpper_currentAbove_noFire() {
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        feedConstantBars(calc, 4);
        double upper = calc.getUpper();
        // both above upper — no downward cross
        assertFalse(calc.isExitSignal(upper + 2.0, upper + 0.5));
    }

    @Test
    void adx_constantPrices_isZero() {
        // Constant prices → +DM = -DM = 0 → DX = 0 → ADX = 0
        BollingerLiveCalculator calc = new BollingerLiveCalculator(PERIOD, K, ADX_PERIOD);
        feedConstantBars(calc, 4);
        assertEquals(0.0, calc.getAdx(), 0.001);
    }

    // --- helpers ---

    private void feedConstantBars(BollingerLiveCalculator calc, int count) {
        for (int i = 0; i < count; i++) {
            calc.update(101.0, 99.0, 100.0);
        }
    }
}
