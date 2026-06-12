import { NextRequest, NextResponse } from 'next/server';
import YahooFinance from 'yahoo-finance2';
import { createClient } from '@supabase/supabase-js';

const yf = new YahooFinance();
const supabase = createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
);

interface Bar { time: number; open: number; high: number; low: number; close: number; volume: number; }

function sma(closes: number[], period: number): (number | null)[] {
    return closes.map((_, i) => i < period - 1 ? null : closes.slice(i - period + 1, i + 1).reduce((a, b) => a + b, 0) / period);
}

function ema(closes: number[], period: number): (number | null)[] {
    const result: (number | null)[] = new Array(closes.length).fill(null);
    const k = 2 / (period + 1);
    let prev: number | null = null;
    for (let i = 0; i < closes.length; i++) {
        if (i < period - 1) { result[i] = null; }
        else if (i === period - 1) { prev = closes.slice(0, period).reduce((a, b) => a + b, 0) / period; result[i] = prev; }
        else { prev = closes[i] * k + prev! * (1 - k); result[i] = prev; }
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
        return { upper: m + stdDevMult * Math.sqrt(variance), lower: m - stdDevMult * Math.sqrt(variance) };
    });
}

function computeSignals(bars: Bar[], strategy: string, params: Record<string, number>): (1 | -1 | 0)[] {
    const closes = bars.map(b => b.close);
    const signals: (1 | -1 | 0)[] = new Array(bars.length).fill(0);

    if (strategy === 'SMA_CROSSOVER') {
        const fast = sma(closes, params.fastPeriod ?? 20);
        const slow = sma(closes, params.slowPeriod ?? 50);
        for (let i = 1; i < bars.length; i++) {
            const [pf, ps, cf, cs] = [fast[i - 1], slow[i - 1], fast[i], slow[i]];
            if (pf != null && ps != null && cf != null && cs != null) {
                if (pf <= ps && cf > cs) signals[i] = 1;
                else if (pf >= ps && cf < cs) signals[i] = -1;
            }
        }
    } else if (strategy === 'EMA_CROSSOVER') {
        const fast = ema(closes, params.fastPeriod ?? 12);
        const slow = ema(closes, params.slowPeriod ?? 26);
        for (let i = 1; i < bars.length; i++) {
            const [pf, ps, cf, cs] = [fast[i - 1], slow[i - 1], fast[i], slow[i]];
            if (pf != null && ps != null && cf != null && cs != null) {
                if (pf <= ps && cf > cs) signals[i] = 1;
                else if (pf >= ps && cf < cs) signals[i] = -1;
            }
        }
    } else if (strategy === 'RSI_OVERSOLD') {
        const rsiVals = rsi(closes, params.period ?? 14);
        const oversold = params.oversoldLevel ?? 30;
        const overbought = params.overboughtLevel ?? 70;
        for (let i = 1; i < bars.length; i++) {
            const [prev, curr] = [rsiVals[i - 1], rsiVals[i]];
            if (prev != null && curr != null) {
                if (prev >= oversold && curr < oversold) signals[i] = 1;
                else if (prev <= overbought && curr > overbought) signals[i] = -1;
            }
        }
    } else if (strategy === 'BOLLINGER_BANDS') {
        const bands = bollingerBands(closes, params.period ?? 20, params.stdDev ?? 2);
        for (let i = 1; i < bars.length; i++) {
            const { upper, lower } = bands[i];
            const prev = bands[i - 1];
            if (upper != null && lower != null && prev.upper != null && prev.lower != null) {
                if (closes[i - 1] >= prev.lower! && closes[i] < lower) signals[i] = 1;
                else if (closes[i - 1] <= prev.upper! && closes[i] > upper) signals[i] = -1;
            }
        }
    }

    return signals;
}

interface PaperTrade {
    entryDate: string;
    entryPrice: number;
    exitDate: string | null;
    exitPrice: number | null;
    shares: number;
    pnl: number | null;
}

function simulate(bars: Bar[], strategy: string, params: Record<string, number>, initialCapital: number) {
    const signals = computeSignals(bars, strategy, params);
    const trades: PaperTrade[] = [];
    let cash = initialCapital;
    let shares = 0;
    let entryPrice = 0;
    let entryDate = '';

    for (let i = 0; i < bars.length; i++) {
        const price = bars[i].close;
        const dateStr = new Date(bars[i].time * 1000).toISOString().split('T')[0];

        if (signals[i] === 1 && shares === 0) {
            const qty = Math.floor((cash * 0.95) / price);
            if (qty > 0) {
                shares = qty;
                entryPrice = price;
                entryDate = dateStr;
                cash -= qty * price * 1.001;
                trades.push({ entryDate: dateStr, entryPrice: price, exitDate: null, exitPrice: null, shares: qty, pnl: null });
            }
        } else if (signals[i] === -1 && shares > 0) {
            const proceeds = shares * price * 0.999;
            const pnl = proceeds - shares * entryPrice * 1.001;
            cash += proceeds;
            if (trades.length > 0) {
                const last = trades[trades.length - 1];
                last.exitDate = dateStr;
                last.exitPrice = price;
                last.pnl = pnl;
            }
            shares = 0;
        }
    }

    const currentPrice = bars[bars.length - 1]?.close ?? 0;
    const portfolioValue = cash + shares * currentPrice;

    let currentSignal: 'BUY' | 'SELL' | 'HOLD' = 'HOLD';
    let lastSignalDate: string | null = null;
    for (let i = bars.length - 1; i >= 0; i--) {
        if (signals[i] !== 0) {
            currentSignal = signals[i] === 1 ? 'BUY' : 'SELL';
            lastSignalDate = new Date(bars[i].time * 1000).toISOString().split('T')[0];
            break;
        }
    }

    return {
        cash,
        shares,
        entryPrice: shares > 0 ? entryPrice : null,
        portfolioValue,
        trades: trades.slice(-10),
        currentSignal,
        lastSignalDate,
    };
}

export async function POST(req: NextRequest) {
    try {
        const { sessionId, symbol, strategy, parameters = {} } = await req.json();
        if (!sessionId || !symbol || !strategy) {
            return NextResponse.json({ message: 'sessionId, symbol, strategy required' }, { status: 400 });
        }

        const sym = symbol.toUpperCase();

        const { data: existing } = await supabase
            .from('paper_portfolios')
            .select('*')
            .eq('session_id', sessionId)
            .eq('symbol', sym)
            .eq('strategy', strategy)
            .maybeSingle();

        let record = existing;

        if (!record) {
            const start = new Date();
            start.setFullYear(start.getFullYear() - 1);
            const { data, error } = await supabase
                .from('paper_portfolios')
                .insert({
                    session_id: sessionId,
                    symbol: sym,
                    strategy,
                    parameters,
                    initial_capital: 100000,
                    start_date: start.toISOString().split('T')[0],
                })
                .select()
                .single();
            if (error) return NextResponse.json({ message: error.message }, { status: 500 });
            record = data;
        }

        const today = new Date().toISOString().split('T')[0];
        type YFBar = { date: Date; open?: number; high?: number; low?: number; close?: number; adjclose?: number; volume?: number };
        const raw = await yf.historical(sym, { period1: record.start_date, period2: today }) as YFBar[];

        if (!raw || raw.length === 0) {
            return NextResponse.json({ message: `No data for ${sym}` }, { status: 400 });
        }

        const bars: Bar[] = raw
            .filter(q => q.open != null && q.close != null)
            .map(q => ({
                time: Math.floor(q.date.getTime() / 1000),
                open: q.open!,
                high: q.high!,
                low: q.low!,
                close: q.adjclose ?? q.close!,
                volume: q.volume ?? 0,
            }))
            .sort((a, b) => a.time - b.time);

        const { cash, shares, entryPrice, portfolioValue, trades, currentSignal, lastSignalDate } =
            simulate(bars, strategy, record.parameters, record.initial_capital);

        return NextResponse.json({
            id: record.id,
            symbol: record.symbol,
            strategy: record.strategy,
            startDate: record.start_date,
            initialCapital: record.initial_capital,
            currentValue: portfolioValue,
            cash,
            shares,
            entryPrice,
            totalPnl: portfolioValue - record.initial_capital,
            totalReturn: (portfolioValue - record.initial_capital) / record.initial_capital,
            currentSignal,
            lastSignalDate,
            trades,
        });
    } catch (err: unknown) {
        return NextResponse.json({ message: err instanceof Error ? err.message : String(err) }, { status: 500 });
    }
}

export async function DELETE(req: NextRequest) {
    try {
        const { sessionId, symbol, strategy } = await req.json();
        await supabase.from('paper_portfolios').delete()
            .eq('session_id', sessionId)
            .eq('symbol', symbol.toUpperCase())
            .eq('strategy', strategy);
        return NextResponse.json({ success: true });
    } catch (err: unknown) {
        return NextResponse.json({ message: err instanceof Error ? err.message : String(err) }, { status: 500 });
    }
}
