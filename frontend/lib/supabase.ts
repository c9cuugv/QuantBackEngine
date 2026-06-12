import { createClient } from '@supabase/supabase-js';

const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;

export const supabase = createClient(url, key);

export interface BacktestRecord {
    id: string;
    created_at: string;
    symbol: string;
    strategy: string;
    start_date: string;
    end_date: string;
    parameters: Record<string, number>;
    metrics: {
        totalReturn: number;
        annualizedReturn: number;
        maxDrawdown: number;
        maxDrawdownPercent: number;
        sharpeRatio: number;
        backtestYears: number;
        totalTrades: number;
        winningTrades: number;
        losingTrades: number;
        winRate: number;
        directionalAccuracy: number;
    };
    trades: unknown[];
    equity_curve: { timestamp: number; value: number }[];
    candles: unknown[];
}
