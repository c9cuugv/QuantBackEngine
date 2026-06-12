'use client';

import { useEffect, useState, useCallback } from 'react';
import { History, TrendingUp, TrendingDown, RefreshCw } from 'lucide-react';

interface HistoryRow {
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
        sharpeRatio: number;
        winRate: number;
        totalTrades: number;
    };
}

const STRATEGY_LABELS: Record<string, string> = {
    SMA_CROSSOVER: 'SMA X',
    EMA_CROSSOVER: 'EMA X',
    RSI_OVERSOLD: 'RSI',
    BOLLINGER_BANDS: 'BB',
};

function fmt(n: number, decimals = 2) {
    return (n * 100).toFixed(decimals) + '%';
}

function relativeTime(iso: string) {
    const diff = Date.now() - new Date(iso).getTime();
    const m = Math.floor(diff / 60000);
    if (m < 1) return 'just now';
    if (m < 60) return `${m}m ago`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h ago`;
    return `${Math.floor(h / 24)}d ago`;
}

export default function BacktestHistory() {
    const [rows, setRows] = useState<HistoryRow[]>([]);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);

    const load = useCallback(async (silent = false) => {
        if (!silent) setLoading(true);
        else setRefreshing(true);
        try {
            const res = await fetch('/api/v1/backtest/history');
            if (res.ok) setRows(await res.json());
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    }, []);

    useEffect(() => {
        load();
        const id = setInterval(() => load(true), 60_000);
        return () => clearInterval(id);
    }, [load]);

    return (
        <section className="card p-6 animate-fade-in">
            <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-3">
                    <History className="w-5 h-5 text-accent-primary" />
                    <h2 className="text-lg font-semibold">Community Backtests</h2>
                    <span className="text-xs text-gray-500 bg-dark-700 px-2 py-0.5 rounded-full">live</span>
                </div>
                <button
                    onClick={() => load(true)}
                    disabled={refreshing}
                    className="text-gray-500 hover:text-white transition-colors"
                    title="Refresh"
                >
                    <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
                </button>
            </div>

            {loading ? (
                <div className="space-y-3">
                    {[...Array(5)].map((_, i) => (
                        <div key={i} className="h-10 skeleton rounded-lg" />
                    ))}
                </div>
            ) : rows.length === 0 ? (
                <p className="text-gray-500 text-sm text-center py-6">No backtests yet — run the first one above.</p>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="text-xs text-gray-500 uppercase tracking-wider border-b border-white/5">
                                <th className="pb-3 text-left font-medium">Symbol</th>
                                <th className="pb-3 text-left font-medium">Strategy</th>
                                <th className="pb-3 text-right font-medium">Return</th>
                                <th className="pb-3 text-right font-medium">Ann.</th>
                                <th className="pb-3 text-right font-medium">Sharpe</th>
                                <th className="pb-3 text-right font-medium">Win%</th>
                                <th className="pb-3 text-right font-medium">Trades</th>
                                <th className="pb-3 text-right font-medium">When</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                            {rows.map((row) => {
                                const pos = row.metrics.totalReturn >= 0;
                                return (
                                    <tr key={row.id} className="group hover:bg-white/[0.02] transition-colors">
                                        <td className="py-3 pr-4">
                                            <span className="font-mono font-semibold text-white">{row.symbol}</span>
                                        </td>
                                        <td className="py-3 pr-4">
                                            <span className="text-xs px-2 py-0.5 rounded-full bg-accent-primary/10 text-accent-primary border border-accent-primary/20">
                                                {STRATEGY_LABELS[row.strategy] ?? row.strategy}
                                            </span>
                                        </td>
                                        <td className="py-3 text-right">
                                            <span className={`font-semibold flex items-center justify-end gap-1 ${pos ? 'text-accent-success' : 'text-accent-danger'}`}>
                                                {pos ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                                                {fmt(row.metrics.totalReturn)}
                                            </span>
                                        </td>
                                        <td className={`py-3 text-right ${row.metrics.annualizedReturn >= 0 ? 'text-accent-success' : 'text-accent-danger'}`}>
                                            {fmt(row.metrics.annualizedReturn)}
                                        </td>
                                        <td className={`py-3 text-right font-mono ${row.metrics.sharpeRatio >= 1 ? 'text-accent-success' : row.metrics.sharpeRatio >= 0 ? 'text-gray-300' : 'text-accent-danger'}`}>
                                            {row.metrics.sharpeRatio.toFixed(2)}
                                        </td>
                                        <td className="py-3 text-right text-gray-300">
                                            {fmt(row.metrics.winRate, 0)}
                                        </td>
                                        <td className="py-3 text-right text-gray-400">
                                            {row.metrics.totalTrades}
                                        </td>
                                        <td className="py-3 text-right text-gray-600 text-xs">
                                            {relativeTime(row.created_at)}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}
