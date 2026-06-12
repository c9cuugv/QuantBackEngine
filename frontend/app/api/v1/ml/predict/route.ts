import { NextRequest, NextResponse } from 'next/server';
import YahooFinance from 'yahoo-finance2';

const yf = new YahooFinance();

export const maxDuration = 60;

interface Bar { time: number; open: number; high: number; low: number; close: number; volume: number; }

// ── Indicators ────────────────────────────────────────────────────────────────

function sma(c: number[], p: number): (number | null)[] {
    return c.map((_, i) => i < p - 1 ? null : c.slice(i - p + 1, i + 1).reduce((a, b) => a + b, 0) / p);
}

function ema(c: number[], p: number): (number | null)[] {
    const r: (number | null)[] = new Array(c.length).fill(null);
    const k = 2 / (p + 1);
    let prev: number | null = null;
    for (let i = 0; i < c.length; i++) {
        if (i < p - 1) r[i] = null;
        else if (i === p - 1) { prev = c.slice(0, p).reduce((a, b) => a + b) / p; r[i] = prev; }
        else { prev = c[i] * k + prev! * (1 - k); r[i] = prev; }
    }
    return r;
}

function rsiIndicator(c: number[], p: number): (number | null)[] {
    const r: (number | null)[] = new Array(c.length).fill(null);
    if (c.length < p + 1) return r;
    let g = 0, l = 0;
    for (let i = 1; i <= p; i++) { const d = c[i] - c[i - 1]; if (d >= 0) g += d; else l -= d; }
    g /= p; l /= p;
    r[p] = 100 - 100 / (1 + g / (l || 1e-9));
    for (let i = p + 1; i < c.length; i++) {
        const d = c[i] - c[i - 1];
        g = (g * (p - 1) + Math.max(d, 0)) / p;
        l = (l * (p - 1) + Math.max(-d, 0)) / p;
        r[i] = 100 - 100 / (1 + g / (l || 1e-9));
    }
    return r;
}

function bb(c: number[], p: number, sd: number) {
    const m = sma(c, p);
    return c.map((_, i) => {
        const mv = m[i];
        if (mv === null || i < p - 1) return { upper: null, lower: null };
        const v = c.slice(i - p + 1, i + 1).reduce((acc, x) => acc + (x - mv) ** 2, 0) / p;
        const s = Math.sqrt(v);
        return { upper: mv + sd * s, lower: mv - sd * s };
    });
}

// ── Feature engineering ───────────────────────────────────────────────────────

const FEATURE_NAMES = ['RSI/100', 'Mom-5d', 'Mom-20d', 'MACD', 'BB-Pos', 'Vol-Ratio'];
const LOOKBACK = 26;

function buildFeatures(bars: Bar[]) {
    const closes = bars.map(b => b.close);
    const vols = bars.map(b => b.volume);
    const rsiV = rsiIndicator(closes, 14);
    const e12 = ema(closes, 12);
    const e26 = ema(closes, 26);
    const bands = bb(closes, 20, 2);

    const X: number[][] = [];
    const y: number[] = [];

    for (let i = LOOKBACK; i < bars.length - 1; i++) {
        if (rsiV[i] == null || e12[i] == null || e26[i] == null || bands[i].upper == null) continue;

        const rsiNorm = rsiV[i]! / 100;
        const mom5 = (closes[i] - closes[i - 5]) / (closes[i - 5] || 1);
        const mom20 = (closes[i] - closes[i - 20]) / (closes[i - 20] || 1);
        const macd = (e12[i]! - e26[i]!) / (closes[i] || 1);
        const bw = (bands[i].upper! - bands[i].lower!);
        const bbPos = bw > 0 ? (closes[i] - bands[i].lower!) / bw : 0.5;
        const avgVol = vols.slice(i - 20, i).reduce((a, b) => a + b, 0) / 20;
        const volRatio = Math.min((vols[i] || 0) / (avgVol || 1), 3) / 3;

        X.push([rsiNorm, mom5, mom20, macd, bbPos, volRatio]);
        y.push(closes[i + 1] > closes[i] ? 1 : 0);
    }

    return { X, y };
}

function lastFeature(bars: Bar[]): number[] | null {
    const closes = bars.map(b => b.close);
    const vols = bars.map(b => b.volume);
    const i = bars.length - 1;
    if (i < LOOKBACK) return null;

    const rsiV = rsiIndicator(closes, 14);
    const e12 = ema(closes, 12);
    const e26 = ema(closes, 26);
    const bands = bb(closes, 20, 2);

    if (rsiV[i] == null || e12[i] == null || e26[i] == null || bands[i].upper == null) return null;

    const rsiNorm = rsiV[i]! / 100;
    const mom5 = (closes[i] - closes[i - 5]) / (closes[i - 5] || 1);
    const mom20 = (closes[i] - closes[i - 20]) / (closes[i - 20] || 1);
    const macd = (e12[i]! - e26[i]!) / (closes[i] || 1);
    const bw = (bands[i].upper! - bands[i].lower!);
    const bbPos = bw > 0 ? (closes[i] - bands[i].lower!) / bw : 0.5;
    const avgVol = vols.slice(i - 20, i).reduce((a, b) => a + b, 0) / 20;
    const volRatio = Math.min((vols[i] || 0) / (avgVol || 1), 3) / 3;

    return [rsiNorm, mom5, mom20, macd, bbPos, volRatio];
}

// ── Logistic Regression ───────────────────────────────────────────────────────

function normalize(Xtr: number[][], Xte: number[][], cur: number[] | null) {
    const nf = Xtr[0].length;
    const means = Array.from({ length: nf }, (_, j) => Xtr.reduce((s, r) => s + r[j], 0) / Xtr.length);
    const stds = Array.from({ length: nf }, (_, j) => {
        const m = means[j];
        return Math.sqrt(Xtr.reduce((s, r) => s + (r[j] - m) ** 2, 0) / Xtr.length) || 1;
    });
    const norm = (X: number[][]) => X.map(r => r.map((x, j) => (x - means[j]) / stds[j]));
    return {
        Xtr: norm(Xtr),
        Xte: norm(Xte),
        cur: cur ? cur.map((x, j) => (x - means[j]) / stds[j]) : null,
        means,
        stds,
    };
}

function sigmoid(z: number) { return 1 / (1 + Math.exp(-Math.max(-50, Math.min(50, z)))); }

function trainLR(X: number[][], y: number[], epochs = 500, lr = 0.05) {
    const nf = X[0].length;
    const w = new Array(nf).fill(0);
    let b = 0;
    for (let e = 0; e < epochs; e++) {
        const dw = new Array(nf).fill(0);
        let db = 0;
        for (let i = 0; i < X.length; i++) {
            const z = X[i].reduce((s, x, j) => s + x * w[j], b);
            const err = sigmoid(z) - y[i];
            for (let j = 0; j < nf; j++) dw[j] += err * X[i][j];
            db += err;
        }
        for (let j = 0; j < nf; j++) w[j] -= lr * dw[j] / X.length;
        b -= lr * db / X.length;
    }
    return { w, b };
}

function predictLR(X: number[][], w: number[], b: number) {
    return X.map(x => {
        const prob = sigmoid(x.reduce((s, xi, j) => s + xi * w[j], b));
        return { prob, label: prob >= 0.5 ? 1 : 0 };
    });
}

function accuracy(preds: { label: number }[], labels: number[]) {
    return preds.filter((p, i) => p.label === labels[i]).length / labels.length;
}

// ── Rule-based directional accuracy (on test period) ─────────────────────────

function ruleBasedAccuracy(
    bars: Bar[],
    strategy: string,
    params: Record<string, number>,
    testStartIdx: number,
): number {
    const closes = bars.map(b => b.close);
    const signals: (1 | -1 | 0)[] = new Array(bars.length).fill(0);

    const e12v = ema(closes, params.fastPeriod ?? 12);
    const e26v = ema(closes, params.slowPeriod ?? 26);
    const sma20 = sma(closes, params.fastPeriod ?? 20);
    const sma50 = sma(closes, params.slowPeriod ?? 50);
    const rsiV = rsiIndicator(closes, params.period ?? 14);
    const bandsV = bb(closes, params.period ?? 20, params.stdDev ?? 2);

    for (let i = 1; i < bars.length; i++) {
        if (strategy === 'SMA_CROSSOVER') {
            const [pf, ps, cf, cs] = [sma20[i - 1], sma50[i - 1], sma20[i], sma50[i]];
            if (pf != null && ps != null && cf != null && cs != null) {
                if (pf <= ps && cf > cs) signals[i] = 1;
                else if (pf >= ps && cf < cs) signals[i] = -1;
            }
        } else if (strategy === 'EMA_CROSSOVER') {
            const [pf, ps, cf, cs] = [e12v[i - 1], e26v[i - 1], e12v[i], e26v[i]];
            if (pf != null && ps != null && cf != null && cs != null) {
                if (pf <= ps && cf > cs) signals[i] = 1;
                else if (pf >= ps && cf < cs) signals[i] = -1;
            }
        } else if (strategy === 'RSI_OVERSOLD') {
            const [prev, curr] = [rsiV[i - 1], rsiV[i]];
            const os = params.oversoldLevel ?? 30, ob = params.overboughtLevel ?? 70;
            if (prev != null && curr != null) {
                if (prev >= os && curr < os) signals[i] = 1;
                else if (prev <= ob && curr > ob) signals[i] = -1;
            }
        } else if (strategy === 'BOLLINGER_BANDS') {
            const { upper, lower } = bandsV[i];
            const prev = bandsV[i - 1];
            if (upper != null && lower != null && prev.upper != null && prev.lower != null) {
                if (closes[i - 1] >= prev.lower! && closes[i] < lower) signals[i] = 1;
                else if (closes[i - 1] <= prev.upper! && closes[i] > upper) signals[i] = -1;
            }
        }
    }

    let correct = 0, total = 0;
    let inPosition = false;
    for (let i = testStartIdx; i < bars.length - 1; i++) {
        if (signals[i] === 1) { inPosition = true; }
        if (signals[i] === -1) { inPosition = false; }
        if (inPosition && signals[i] !== 0) {
            total++;
            if (signals[i] === 1 && closes[i + 1] > closes[i]) correct++;
            if (signals[i] === -1 && closes[i + 1] < closes[i]) correct++;
        }
    }
    return total > 0 ? correct / total : 0;
}

// ── Handler ───────────────────────────────────────────────────────────────────

export async function POST(req: NextRequest) {
    try {
        const { symbol, strategy = 'SMA_CROSSOVER', parameters = {} } = await req.json();
        if (!symbol) return NextResponse.json({ message: 'symbol required' }, { status: 400 });

        const sym = symbol.toUpperCase();
        const start = new Date();
        start.setFullYear(start.getFullYear() - 3);
        const today = new Date().toISOString().split('T')[0];

        type YFBar = { date: Date; open?: number; high?: number; low?: number; close?: number; adjclose?: number; volume?: number };
        const raw = await yf.historical(sym, { period1: start.toISOString().split('T')[0], period2: today }) as YFBar[];

        if (!raw || raw.length < 60) {
            return NextResponse.json({ message: 'Insufficient historical data' }, { status: 400 });
        }

        const bars: Bar[] = raw
            .filter(q => q.open != null && q.close != null)
            .map(q => ({
                time: Math.floor(q.date.getTime() / 1000),
                open: q.open!, high: q.high!, low: q.low!,
                close: q.adjclose ?? q.close!, volume: q.volume ?? 0,
            }))
            .sort((a, b) => a.time - b.time);

        const { X, y } = buildFeatures(bars);
        if (X.length < 40) return NextResponse.json({ message: 'Not enough data for training' }, { status: 400 });

        const splitIdx = Math.floor(X.length * 0.8);
        const Xtr = X.slice(0, splitIdx), ytr = y.slice(0, splitIdx);
        const Xte = X.slice(splitIdx), yte = y.slice(splitIdx);

        const curRaw = lastFeature(bars);
        const { Xtr: XtrN, Xte: XteN, cur: curN } = normalize(Xtr, Xte, curRaw);

        const { w, b } = trainLR(XtrN, ytr);

        const trainAcc = accuracy(predictLR(XtrN, w, b), ytr);
        const testPreds = predictLR(XteN, w, b);
        const testAcc = accuracy(testPreds, yte);

        const currentPred = curN ? predictLR([curN], w, b)[0] : null;

        // Rule-based accuracy on same test bars
        const testBarStartIdx = LOOKBACK + splitIdx;
        const ruleAcc = ruleBasedAccuracy(bars, strategy, parameters, testBarStartIdx);

        // Feature importances (abs weight, normalized)
        const absW = w.map(Math.abs);
        const maxW = Math.max(...absW) || 1;
        const importances = FEATURE_NAMES.map((name, i) => ({
            name,
            weight: w[i],
            importance: absW[i] / maxW,
        })).sort((a, b) => Math.abs(b.weight) - Math.abs(a.weight));

        return NextResponse.json({
            symbol: sym,
            strategy,
            trainSamples: Xtr.length,
            testSamples: Xte.length,
            trainAccuracy: trainAcc,
            testAccuracy: testAcc,
            ruleBasedAccuracy: ruleAcc,
            mlEdge: testAcc - ruleAcc,
            currentPrediction: currentPred,
            importances,
        });
    } catch (err: unknown) {
        return NextResponse.json({ message: err instanceof Error ? err.message : String(err) }, { status: 500 });
    }
}
