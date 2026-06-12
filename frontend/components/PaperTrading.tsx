'use client';

import { useState, useEffect, useCallback } from 'react';
import { TrendingUp, TrendingDown, Minus, DollarSign, RotateCcw, Loader2 } from 'lucide-react';
import { getSessionId } from '@/lib/session';

interface PaperTrade {
    entryDate: string;
    entryPrice: number;
    exitDate: string | null;
    exitPrice: number | null;
    shares: number;
    pnl: number | null;
}

interface PortfolioState {
    id: string;
    symbol: string;
    strategy: string;
    startDate: string;
    initialCapital: number;
    currentValue: number;
    cash: number;
    shares: number;
    entryPrice: number | null;
    totalPnl: number;
    totalReturn: number;
    currentSignal: 'BUY' | 'SELL' | 'HOLD';
    lastSignalDate: string | null;
    trades: PaperTrade[];
}

interface Props {
    symbol: string;
    strategy: string;
    parameters: Record<string, number>;
    strategyName: string;
}

const SIGNAL_STYLES = {
    BUY:  { label: 'BUY SIGNAL',  cls: 'text-accent-success bg-accent-success/10 border-accent-success/30' },
    SELL: { label: 'SELL SIGNAL', cls: 'text-accent-danger bg-accent-danger/10 border-accent-danger/30' },
    HOLD: { label: 'HOLD',        cls: 'text-gray-400 bg-white/5 border-white/10' },
};

const fmt = (n: number) =>
    new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(n);

export default function PaperTrading({ symbol, strategy, parameters, strategyName }: Props) {
    const [portfolio, setPortfolio] = useState<PortfolioState | null>(null);
    const [loading, setLoading] = useState(false);
    const [resetting, setResetting] = useState(false);
    const [sessionId, setSessionId] = useState('');

    useEffect(() => { setSessionId(getSessionId()); }, []);

    const load = useCallback(async (sid: string) => {
        if (!sid || !symbol || !strategy) return;
        setLoading(true);
        try {
            const res = await fetch('/api/v1/paper/portfolio', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sessionId: sid, symbol, strategy, parameters }),
            });
            if (res.ok) setPortfolio(await res.json());
        } finally { setLoading(false); }
    }, [symbol, strategy, parameters]);

    useEffect(() => { if (sessionId) load(sessionId); }, [sessionId, load]);

    const reset = async () => {
        setResetting(true);
        await fetch('/api/v1/paper/portfolio', {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId, symbol, strategy }),
        });
        setPortfolio(null);
        await load(sessionId);
        setResetting(false);
    };

    const sig = portfolio ? SIGNAL_STYLES[portfolio.currentSignal] : null;
    const up = (portfolio?.totalReturn ?? 0) >= 0;

    return (
        <section className="card p-6 animate-fade-in">
            <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-3">
                    <DollarSign className="w-5 h-5 text-accent-primary" />
                    <h2 className="text-lg font-semibold">Paper Portfolio</h2>
                    {sig && (
                        <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${sig.cls}`}>
                            {sig.label}
                        </span>
                    )}
                </div>
                <div className="flex items-center gap-3 text-xs text-gray-500">
                    {portfolio && <span className="font-mono">{portfolio.symbol} · {strategyName}</span>}
                    <button
                        onClick={reset}
                        disabled={resetting || loading}
                        className="flex items-center gap-1 hover:text-white transition-colors disabled:opacity-40"
                        title="Reset — restarts simulation from today"
                    >
                        <RotateCcw className={`w-3.5 h-3.5 ${resetting ? 'animate-spin' : ''}`} />
                        Reset
                    </button>
                </div>
            </div>

            {loading && !portfolio ? (
                <div className="flex items-center justify-center py-10 text-gray-500 gap-2">
                    <Loader2 className="w-5 h-5 animate-spin" />
                    Simulating paper portfolio...
                </div>
            ) : portfolio ? (
                <>
                    {/* Stats grid */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-5">
                        <div className="bg-dark-700 rounded-xl p-4">
                            <p className="text-xs text-gray-500 mb-1">Portfolio Value</p>
                            <p className="text-xl font-bold">{fmt(portfolio.currentValue)}</p>
                        </div>
                        <div className="bg-dark-700 rounded-xl p-4">
                            <p className="text-xs text-gray-500 mb-1">Total P&L</p>
                            <p className={`text-xl font-bold flex items-center gap-1 ${up ? 'text-accent-success' : 'text-accent-danger'}`}>
                                {up ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                                {up ? '+' : ''}{fmt(portfolio.totalPnl)}
                            </p>
                        </div>
                        <div className="bg-dark-700 rounded-xl p-4">
                            <p className="text-xs text-gray-500 mb-1">Return</p>
                            <p className={`text-xl font-bold ${up ? 'text-accent-success' : 'text-accent-danger'}`}>
                                {up ? '+' : ''}{(portfolio.totalReturn * 100).toFixed(2)}%
                            </p>
                        </div>
                        <div className="bg-dark-700 rounded-xl p-4">
                            <p className="text-xs text-gray-500 mb-1">Position</p>
                            {portfolio.shares > 0 ? (
                                <p className="text-xl font-bold font-mono">
                                    {portfolio.shares} <span className="text-sm text-gray-400">sh</span>
                                </p>
                            ) : (
                                <p className="text-xl font-bold text-gray-400 flex items-center gap-1">
                                    <Minus className="w-4 h-4" /> Cash
                                </p>
                            )}
                        </div>
                    </div>

                    {/* Meta row */}
                    <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-500 mb-5">
                        <span>Started {portfolio.startDate}</span>
                        <span>·</span>
                        <span>Cash {fmt(portfolio.cash)}</span>
                        {portfolio.entryPrice && <><span>·</span><span>Entry @ {fmt(portfolio.entryPrice)}</span></>}
                        {portfolio.lastSignalDate && <><span>·</span><span>Last signal {portfolio.lastSignalDate}</span></>}
                    </div>

                    {/* Trade history */}
                    {portfolio.trades.length > 0 && (
                        <>
                            <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">Recent Trades</p>
                            <div className="overflow-x-auto">
                                <table className="w-full text-xs">
                                    <thead>
                                        <tr className="text-gray-500 border-b border-white/5">
                                            <th className="pb-2 text-left font-medium">Dir</th>
                                            <th className="pb-2 text-left font-medium">Entry</th>
                                            <th className="pb-2 text-right font-medium">Price</th>
                                            <th className="pb-2 text-left font-medium">Exit</th>
                                            <th className="pb-2 text-right font-medium">Price</th>
                                            <th className="pb-2 text-right font-medium">Shares</th>
                                            <th className="pb-2 text-right font-medium">P&L</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-white/5">
                                        {portfolio.trades.map((t, i) => (
                                            <tr key={i} className="hover:bg-white/[0.02] transition-colors">
                                                <td className="py-2">
                                                    {t.exitPrice == null
                                                        ? <span className="text-accent-primary font-bold">▲</span>
                                                        : t.exitPrice > t.entryPrice
                                                            ? <span className="text-accent-success font-bold">✓</span>
                                                            : <span className="text-accent-danger font-bold">✗</span>}
                                                </td>
                                                <td className="py-2 text-gray-300">{t.entryDate}</td>
                                                <td className="py-2 text-right font-mono">{fmt(t.entryPrice)}</td>
                                                <td className="py-2 text-gray-300">
                                                    {t.exitDate ?? <span className="text-accent-primary italic">open</span>}
                                                </td>
                                                <td className="py-2 text-right font-mono">{t.exitPrice ? fmt(t.exitPrice) : '—'}</td>
                                                <td className="py-2 text-right font-mono text-gray-400">{t.shares}</td>
                                                <td className={`py-2 text-right font-mono font-semibold ${
                                                    t.pnl == null ? 'text-gray-500' : t.pnl >= 0 ? 'text-accent-success' : 'text-accent-danger'
                                                }`}>
                                                    {t.pnl == null ? '—' : `${t.pnl >= 0 ? '+' : ''}${fmt(t.pnl)}`}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}

                    {portfolio.trades.length === 0 && (
                        <p className="text-xs text-gray-600 text-center py-4">
                            No signals fired yet since {portfolio.startDate} — strategy is waiting for an entry.
                        </p>
                    )}
                </>
            ) : null}
        </section>
    );
}
