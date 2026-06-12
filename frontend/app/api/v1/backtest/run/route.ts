import { NextRequest, NextResponse } from 'next/server';
import YahooFinance from 'yahoo-finance2';
import { createClient } from '@supabase/supabase-js';

const yf = new YahooFinance();
const supabase = createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
);

interface Bar {
    time: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
}

interface Trade {
    type: 'BUY' | 'SELL';
    entryDate: string;
    entryPrice: number;
    exitDate: string;
    exitPrice: number;
    shares: number;
    pnl: number;
    commission: number;
}

function sma(closes: number[], period: number): (number | null)[] {
    return closes.map((_, i) => {
        if (i < period - 1) return null;
        return closes.slice(i - period + 1, i + 1).reduce((a, b) => a + b, 0) / period;
    });
}

function ema(closes: number[], period: number): (number | null)[] {
    const result: (number | null)[] = new Array(closes.length).fill(null);
    const k = 2 / (period + 1);
    let prev: number | null = null;
    for (let i = 0; i < closes.length; i++) {
        if (i < period - 1) {
            result[i] = null;
        } else if (i === period - 1) {
            prev = closes.slice(0, period).reduce((a, b) => a + b, 0) / period;
            result[i] = prev;
        } else {
            prev = closes[i] * k + prev! * (1 - k);
            result[i] = prev;
        }
    }
    return result;
}

function rsi(closes: number[], period: number): (number | null)[] {
    const result: (number | null)[] = new Array(closes.length).fill(null);
    if (closes.length < period + 1) return result;
    let avgGain = 0, avgLoss = 0;
    for (let i = 1; i <= period; i++) {
        const diff = closes[i] - closes[i - 1];
        if (diff >= 0) avgGain += diff; else avgLoss -= diff;
    }
    avgGain /= period; avgLoss /= period;
    result[period] = 100 - 100 / (1 + avgGain / (avgLoss || 1e-9));
    for (let i = period + 1; i < closes.length; i++) {
        const diff = closes[i] - closes[i - 1];
        avgGain = (avgGain * (period - 1) + Math.max(diff, 0)) / period;
        avgLoss = (avgLoss * (period - 1) + Math.max(-diff, 0)) / period;
        result[i] = 100 - 100 / (1 + avgGain / (avgLoss || 1e-9));
    }
    return result;
}

function bollingerBands(closes: number[], period: number, stdDevMult: number) {
    const mid = sma(closes, period);
    return closes.map((_, i) => {
        const m = mid[i];
        if (m === null || i < period - 1) return { upper: null, lower: null };
        const slice = closes.slice(i - period + 1, i + 1);
        const variance = slice.reduce((acc, v) => acc + (v - m) ** 2, 0) / period;
        const std = Math.sqrt(variance);
        return { upper: m + stdDevMult * std, lower: m - stdDevMult * std };
    });
}

function runStrategy(
    bars: Bar[],
    strategy: string,
    params: Record<string, number>,
    initialCapital: number,
    commissionRate: number,
): { trades: Trade[]; equityCurve: { timestamp: number; value: number }[] } {
    const closes = bars.map((b) => b.close);
    const trades: Trade[] = [];
    const equityCurve: { timestamp: number; value: number }[] = [];
    let capital = initialCapital;
    let position = 0;
    let entryPrice = 0;
    let entryDate = '';

    // Compute signals
    let signals: (1 | -1 | 0)[] = new Array(bars.length).fill(0);

    if (strategy === 'SMA_CROSSOVER') {
        const fast = sma(closes, params.fastPeriod ?? 20);
        const slow = sma(closes, params.slowPeriod ?? 50);
        for (let i = 1; i < bars.length; i++) {
            const pf = fast[i - 1], ps = slow[i - 1], cf = fast[i], cs = slow[i];
            if (pf !== null && ps !== null && cf !== null && cs !== null) {
                if (pf <= ps && cf > cs) signals[i] = 1;
                else if (pf >= ps && cf < cs) signals[i] = -1;
            }
        }
    } else if (strategy === 'EMA_CROSSOVER') {
        const fast = ema(closes, params.fastPeriod ?? 12);
        const slow = ema(closes, params.slowPeriod ?? 26);
        for (let i = 1; i < bars.length; i++) {
            const pf = fast[i - 1], ps = slow[i - 1], cf = fast[i], cs = slow[i];
            if (pf !== null && ps !== null && cf !== null && cs !== null) {
                if (pf <= ps && cf > cs) signals[i] = 1;
                else if (pf >= ps && cf < cs) signals[i] = -1;
            }
        }
    } else if (strategy === 'RSI_OVERSOLD') {
        const rsiVals = rsi(closes, params.period ?? 14);
        const oversold = params.oversoldLevel ?? 30;
        const overbought = params.overboughtLevel ?? 70;
        for (let i = 1; i < bars.length; i++) {
            const prev = rsiVals[i - 1], curr = rsiVals[i];
            if (prev !== null && curr !== null) {
                if (prev >= oversold && curr < oversold) signals[i] = 1;
                else if (prev <= overbought && curr > overbought) signals[i] = -1;
            }
        }
    } else if (strategy === 'BOLLINGER_BANDS') {
        const bands = bollingerBands(closes, params.period ?? 20, params.stdDev ?? 2);
        for (let i = 1; i < bars.length; i++) {
            const { upper, lower } = bands[i];
            const prev = bands[i - 1];
            if (upper !== null && lower !== null && prev.upper !== null && prev.lower !== null) {
                if (closes[i - 1] >= prev.lower! && closes[i] < lower) signals[i] = 1;
                else if (closes[i - 1] <= prev.upper! && closes[i] > upper) signals[i] = -1;
            }
        }
    }

    for (let i = 0; i < bars.length; i++) {
        const bar = bars[i];
        const price = bar.close;
        const sig = signals[i];
        const dateStr = new Date(bar.time * 1000).toISOString().split('T')[0];

        if (sig === 1 && position === 0) {
            const shares = Math.floor((capital * 0.95) / price);
            if (shares > 0) {
                const commission = shares * price * commissionRate;
                position = shares;
                entryPrice = price;
                entryDate = dateStr;
                capital -= shares * price + commission;
            }
        } else if (sig === -1 && position > 0) {
            const commission = position * price * commissionRate;
            const pnl = position * (price - entryPrice) - commission - position * entryPrice * commissionRate;
            trades.push({
                type: 'BUY',
                entryDate,
                entryPrice,
                exitDate: dateStr,
                exitPrice: price,
                shares: position,
                pnl,
                commission,
            });
            capital += position * price - commission;
            position = 0;
        }

        const portfolioValue = capital + position * price;
        equityCurve.push({ timestamp: bar.time, value: portfolioValue });
    }

    // Close open position at end
    if (position > 0) {
        const lastBar = bars[bars.length - 1];
        const price = lastBar.close;
        const commission = position * price * commissionRate;
        const pnl = position * (price - entryPrice) - commission - position * entryPrice * commissionRate;
        trades.push({
            type: 'BUY',
            entryDate,
            entryPrice,
            exitDate: new Date(lastBar.time * 1000).toISOString().split('T')[0],
            exitPrice: price,
            shares: position,
            pnl,
            commission,
        });
    }

    return { trades, equityCurve };
}

function calcMetrics(
    trades: Trade[],
    equityCurve: { timestamp: number; value: number }[],
    initialCapital: number,
) {
    if (equityCurve.length < 2) {
        return { totalReturn: 0, annualizedReturn: 0, maxDrawdown: 0, maxDrawdownPercent: 0, sharpeRatio: 0, backtestYears: 0, totalTrades: 0, winningTrades: 0, losingTrades: 0, winRate: 0 };
    }
    const finalValue = equityCurve[equityCurve.length - 1].value;
    const totalReturn = (finalValue - initialCapital) / initialCapital;
    const years = (equityCurve[equityCurve.length - 1].timestamp - equityCurve[0].timestamp) / (365.25 * 86400);
    const annualizedReturn = years > 0 ? Math.pow(1 + totalReturn, 1 / years) - 1 : 0;

    let peak = initialCapital, maxDrawdown = 0, maxDrawdownAbs = 0;
    for (const pt of equityCurve) {
        if (pt.value > peak) peak = pt.value;
        const dd = (peak - pt.value) / peak;
        if (dd > maxDrawdown) { maxDrawdown = dd; maxDrawdownAbs = peak - pt.value; }
    }

    const dailyReturns: number[] = [];
    for (let i = 1; i < equityCurve.length; i++) {
        dailyReturns.push((equityCurve[i].value - equityCurve[i - 1].value) / equityCurve[i - 1].value);
    }
    const meanR = dailyReturns.reduce((a, b) => a + b, 0) / dailyReturns.length;
    const stdR = Math.sqrt(dailyReturns.reduce((a, b) => a + (b - meanR) ** 2, 0) / dailyReturns.length);
    const sharpeRatio = stdR > 0 ? ((meanR * 252 - 0.02) / (stdR * Math.sqrt(252))) : 0;

    const directionallyCorrect = trades.filter((t) => t.exitPrice > t.entryPrice).length;

    return {
        totalReturn,
        annualizedReturn,
        maxDrawdown: maxDrawdownAbs,
        maxDrawdownPercent: maxDrawdown,
        sharpeRatio,
        backtestYears: years,
        totalTrades: trades.length,
        winningTrades: trades.filter((t) => t.pnl > 0).length,
        losingTrades: trades.filter((t) => t.pnl <= 0).length,
        winRate: trades.length > 0 ? trades.filter((t) => t.pnl > 0).length / trades.length : 0,
        directionalAccuracy: trades.length > 0 ? directionallyCorrect / trades.length : 0,
    };
}

export async function POST(req: NextRequest) {
    try {
        const body = await req.json();
        const {
            symbol,
            strategy,
            parameters = {},
            startDate,
            endDate,
            initialCapital = 100000,
            commissionRate = 0.001,
        } = body;

        if (!symbol || !strategy || !startDate || !endDate) {
            return NextResponse.json({ message: 'symbol, strategy, startDate, endDate required' }, { status: 400 });
        }

        type YFBar = { date: Date; open?: number; high?: number; low?: number; close?: number; adjclose?: number; volume?: number };
        const raw = (await yf.historical(symbol.toUpperCase(), {
            period1: startDate,
            period2: endDate,
        })) as YFBar[];

        if (!raw || raw.length === 0) {
            return NextResponse.json({ message: `No data found for symbol: ${symbol}` }, { status: 400 });
        }

        const bars: Bar[] = raw
            .filter((q) => q.open != null && q.close != null)
            .map((q) => ({
                time: Math.floor(q.date.getTime() / 1000),
                open: q.open!,
                high: q.high!,
                low: q.low!,
                close: q.adjclose ?? q.close!,
                volume: q.volume ?? 0,
            }))
            .sort((a, b) => a.time - b.time);

        const { trades, equityCurve } = runStrategy(bars, strategy, parameters, initialCapital, commissionRate);
        const metrics = calcMetrics(trades, equityCurve, initialCapital);

        // Save to Supabase (non-blocking — failure doesn't affect response)
        supabase.from('backtest_results').insert({
            symbol,
            strategy,
            start_date: startDate,
            end_date: endDate,
            parameters,
            metrics,
            trades,
            equity_curve: equityCurve,
            candles: bars,
        }).then(({ error }) => {
            if (error) console.error('[supabase] save failed:', error.message);
        });

        return NextResponse.json({
            id: `${symbol}-${strategy}-${Date.now()}`,
            symbol,
            strategy,
            metrics,
            trades,
            equityCurve,
            candles: bars,
        });
    } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return NextResponse.json({ message: msg }, { status: 500 });
    }
}
