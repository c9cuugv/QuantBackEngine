import { createClient, SupabaseClient } from '@supabase/supabase-js';

let _client: SupabaseClient | null = null;

// Lazy init — createClient must not run at module load time: Next.js build-time
// page-data collection imports modules without env vars, which would crash the build.
export function getSupabase(): SupabaseClient {
    if (!_client) {
        _client = createClient(
            process.env.NEXT_PUBLIC_SUPABASE_URL!,
            process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
        );
    }
    return _client;
}

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
