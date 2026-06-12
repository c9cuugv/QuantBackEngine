'use client';

import { useState, useEffect, useCallback } from 'react';
import {
    TrendingUp,
    TrendingDown,
    Play,
    BarChart3,
    Settings2,
    Zap,
    Calendar,
    DollarSign,
    Percent,
    Activity,
    Target,
    Award,
    Info,
} from 'lucide-react';

const POPULAR_SYMBOLS = ['AAPL', 'MSFT', 'GOOGL', 'TSLA', 'NVDA', 'META', 'AMZN', 'SPY', 'QQQ', 'BTC-USD'];

const STRATEGY_INFO: Record<string, { how: string; signals: string; tips: string }> = {
    SMA_CROSSOVER: {
        how: 'Trend-following. Buys when the fast SMA crosses above the slow SMA (golden cross). Sells when fast crosses below slow (death cross).',
        signals: 'BUY when Fast SMA rises above Slow SMA · SELL when Fast SMA falls below Slow SMA',
        tips: 'Fast=20, Slow=50 is classic swing-trade setup. Fast=50, Slow=200 is the long-term golden/death cross.',
    },
    EMA_CROSSOVER: {
        how: 'Same as SMA Crossover but uses Exponential Moving Averages — they weight recent prices more heavily, reacting faster to trend changes.',
        signals: 'BUY when Fast EMA rises above Slow EMA · SELL when Fast EMA falls below Slow EMA',
        tips: 'Fast=12, Slow=26 is the MACD default. EMA reacts faster than SMA — expect more trades.',
    },
    RSI_OVERSOLD: {
        how: 'Mean-reversion. RSI (0–100) measures momentum. Buys when RSI drops below the oversold level (price is likely cheap). Sells when RSI rises above overbought (price is likely expensive).',
        signals: 'BUY when RSI drops below Oversold Level (default 30) · SELL when RSI rises above Overbought Level (default 70)',
        tips: 'Standard: Oversold=30, Overbought=70. Use Oversold=20/Overbought=80 for stronger (rarer) signals only.',
    },
    BOLLINGER_BANDS: {
        how: 'Mean-reversion. Bands are 2 standard deviations around a moving average. Price touching the lower band signals oversold (buy). Touching the upper band signals overbought (sell).',
        signals: 'BUY when price touches lower band · SELL when price touches upper band',
        tips: 'Period=20, StdDev=2 is standard (covers ~95% of price action). Increase StdDev for fewer, stronger signals.',
    },
};
import TradingChart from '@/components/TradingChart';
import MetricCard from '@/components/MetricCard';
import TradeList from '@/components/TradeList';
import EquityCurve from '@/components/EquityCurve';
import StrategySummary from '@/components/StrategySummary';
import BacktestHistory from '@/components/BacktestHistory';

interface SymbolInfo {
    symbol: string;
    name: string;
    type: string;
}

interface Strategy {
    id: string;
    name: string;
    description: string;
    parameters: {
        name: string;
        type: string;
        defaultValue: number;
        minValue: number;
        maxValue: number;
        description: string;
    }[];
}

interface BacktestResult {
    id: string;
    symbol: string;
    strategy: string;
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
    };
    trades: {
        type: string;
        entryDate: string;
        entryPrice: number;
        exitDate: string;
        exitPrice: number;
        shares: number;
        pnl: number;
        commission: number;
    }[];
    equityCurve: { timestamp: number; value: number }[];
    candles: { time: number; open: number; high: number; low: number; close: number; volume: number }[];
}

export default function Dashboard() {
    const [strategies, setStrategies] = useState<Strategy[]>([]);
    const [selectedStrategy, setSelectedStrategy] = useState<string>('SMA_CROSSOVER');
    const [symbol, setSymbol] = useState('AAPL');
    const [startDate, setStartDate] = useState('2020-01-01');
    const [endDate, setEndDate] = useState('2024-12-31');
    const [parameters, setParameters] = useState<Record<string, number>>({});
    const [result, setResult] = useState<BacktestResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [symbols, setSymbols] = useState<SymbolInfo[]>([]);
    const [showStrategyInfo, setShowStrategyInfo] = useState(false);

    // Fetch available strategies and symbols on mount
    useEffect(() => {
        fetchStrategies();
        fetchSymbols();
    }, []);

    // Fetch available symbols
    const fetchSymbols = useCallback(async () => {
        try {
            const res = await fetch('/api/v1/market-data/symbols');
            if (res.ok) {
                const data: string[] = await res.json();
                setSymbols(data.map((s) => ({ symbol: s, name: s, type: 'predefined' })));
            }
        } catch (err) {
            console.error('Failed to fetch symbols:', err);
        }
    }, []);

    // Update default parameters when strategy changes
    useEffect(() => {
        const strategy = strategies.find((s) => s.id === selectedStrategy);
        if (strategy) {
            const defaults: Record<string, number> = {};
            strategy.parameters.forEach((p) => {
                defaults[p.name] = p.defaultValue;
            });
            setParameters(defaults);
        }
    }, [selectedStrategy, strategies]);

    const fetchStrategies = async () => {
        try {
            const res = await fetch('/api/v1/backtest/strategies');
            if (res.ok) {
                const data = await res.json();
                setStrategies(data);
            }
        } catch (err) {
            console.error('Failed to fetch strategies:', err);
            // Use default strategies if API is not available
            setStrategies([
                {
                    id: 'SMA_CROSSOVER',
                    name: 'SMA Crossover',
                    description: 'Trend-following strategy using moving average crossovers',
                    parameters: [
                        { name: 'shortPeriod', type: 'INTEGER', defaultValue: 50, minValue: 5, maxValue: 100, description: 'Short SMA period' },
                        { name: 'longPeriod', type: 'INTEGER', defaultValue: 200, minValue: 50, maxValue: 500, description: 'Long SMA period' },
                    ],
                },
                {
                    id: 'RSI',
                    name: 'RSI Momentum',
                    description: 'Mean-reversion strategy using RSI indicator',
                    parameters: [
                        { name: 'period', type: 'INTEGER', defaultValue: 14, minValue: 5, maxValue: 50, description: 'RSI period' },
                        { name: 'oversoldThreshold', type: 'INTEGER', defaultValue: 30, minValue: 10, maxValue: 40, description: 'Oversold level' },
                        { name: 'overboughtThreshold', type: 'INTEGER', defaultValue: 70, minValue: 60, maxValue: 90, description: 'Overbought level' },
                    ],
                },
            ]);
        }
    };

    const runBacktest = async () => {
        setLoading(true);
        setError(null);

        try {
            const res = await fetch('/api/v1/backtest/run', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    symbol,
                    strategy: selectedStrategy,
                    parameters,
                    startDate,
                    endDate,
                    initialCapital: 100000,
                    commissionRate: 0.001,
                }),
            });

            if (!res.ok) {
                const errData = await res.json();
                throw new Error(errData.message || 'Backtest failed');
            }

            const data = await res.json();
            setResult(data);
        } catch (err) {
            console.error('Backtest error:', err);
            setError(err instanceof Error ? err.message : 'An error occurred');
        } finally {
            setLoading(false);
        }
    };

    const currentStrategy = strategies.find((s) => s.id === selectedStrategy);

    return (
        <div className="min-h-screen bg-dark-900">
            {/* Header */}
            <header className="glass border-b border-white/5 sticky top-0 z-50">
                <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="p-2 rounded-xl bg-gradient-to-br from-accent-primary to-accent-secondary">
                            <Zap className="w-6 h-6 text-white" />
                        </div>
                        <div>
                            <h1 className="text-xl font-bold gradient-text">QuantBackEngine</h1>
                            <p className="text-xs text-gray-500">Algorithmic Trading Platform</p>
                        </div>
                    </div>
                    <div className="flex items-center gap-4 text-sm text-gray-400">
                        <span className="flex items-center gap-2">
                            <div className="w-2 h-2 rounded-full bg-accent-success animate-pulse"></div>
                            API Connected
                        </span>
                    </div>
                </div>
            </header>

            <main className="max-w-7xl mx-auto px-6 py-8">
                {/* Configuration Panel */}
                <section className="card p-6 mb-8 animate-fade-in">
                    <div className="flex items-center gap-3 mb-6">
                        <Settings2 className="w-5 h-5 text-accent-primary" />
                        <h2 className="text-lg font-semibold">Backtest Configuration</h2>
                    </div>

                    {/* Quick-select popular symbols */}
                    <div className="mb-5">
                        <p className="text-xs text-gray-500 mb-2">Popular symbols</p>
                        <div className="flex flex-wrap gap-2">
                            {POPULAR_SYMBOLS.map((s) => (
                                <button
                                    key={s}
                                    onClick={() => setSymbol(s)}
                                    className={`px-3 py-1 rounded-lg text-xs font-medium transition-all border ${
                                        symbol === s
                                            ? 'bg-accent-primary border-accent-primary text-white'
                                            : 'bg-dark-700 border-white/10 text-gray-400 hover:border-accent-primary hover:text-white'
                                    }`}
                                >
                                    {s}
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                        {/* Symbol */}
                        <div>
                            <label className="block text-sm text-gray-400 mb-2">Stock Symbol</label>
                            <input
                                type="text"
                                value={symbol}
                                onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                                placeholder="Any ticker, e.g. AAPL"
                                className="input"
                            />
                            <p className="text-xs text-gray-600 mt-1">Type any ticker or click above</p>
                        </div>

                        {/* Strategy */}
                        <div>
                            <label className="block text-sm text-gray-400 mb-2 flex items-center gap-1">
                                Strategy
                                <button
                                    onClick={() => setShowStrategyInfo(!showStrategyInfo)}
                                    className="text-gray-600 hover:text-accent-primary transition-colors"
                                    title="Show strategy info"
                                >
                                    <Info className="w-3.5 h-3.5" />
                                </button>
                            </label>
                            <select
                                value={selectedStrategy}
                                onChange={(e) => { setSelectedStrategy(e.target.value); setShowStrategyInfo(true); }}
                                className="select"
                            >
                                {strategies.map((s) => (
                                    <option key={s.id} value={s.id}>
                                        {s.name}
                                    </option>
                                ))}
                            </select>
                        </div>

                        {/* Date Range */}
                        <div>
                            <label className="block text-sm text-gray-400 mb-2">Start Date</label>
                            <input
                                type="date"
                                value={startDate}
                                onChange={(e) => setStartDate(e.target.value)}
                                className="input"
                            />
                        </div>

                        <div>
                            <label className="block text-sm text-gray-400 mb-2">End Date</label>
                            <input
                                type="date"
                                value={endDate}
                                onChange={(e) => setEndDate(e.target.value)}
                                className="input"
                            />
                        </div>
                    </div>

                    {/* Strategy Info Panel */}
                    {showStrategyInfo && currentStrategy && STRATEGY_INFO[currentStrategy.id] && (
                        <div className="mt-6 pt-6 border-t border-white/5">
                            <div className="rounded-xl bg-dark-700 border border-accent-primary/20 p-4">
                                <div className="flex items-start justify-between mb-3">
                                    <h3 className="text-sm font-semibold text-accent-primary flex items-center gap-2">
                                        <Info className="w-4 h-4" />
                                        {currentStrategy.name} — How it works
                                    </h3>
                                    <button onClick={() => setShowStrategyInfo(false)} className="text-gray-600 hover:text-white text-xs">✕</button>
                                </div>
                                <p className="text-sm text-gray-300 mb-3">{STRATEGY_INFO[currentStrategy.id].how}</p>
                                <div className="mb-3">
                                    <p className="text-xs text-gray-500 font-medium mb-1">SIGNALS</p>
                                    <p className="text-xs text-gray-400 font-mono bg-dark-800 rounded px-3 py-2">{STRATEGY_INFO[currentStrategy.id].signals}</p>
                                </div>
                                <div>
                                    <p className="text-xs text-gray-500 font-medium mb-1">TIPS</p>
                                    <p className="text-xs text-gray-400">{STRATEGY_INFO[currentStrategy.id].tips}</p>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Strategy Parameters */}
                    {currentStrategy && currentStrategy.parameters.length > 0 && (
                        <div className="mt-6 pt-6 border-t border-white/5">
                            <h3 className="text-sm text-gray-400 mb-4 flex items-center gap-2">
                                <BarChart3 className="w-4 h-4" />
                                Strategy Parameters
                            </h3>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                {currentStrategy.parameters.map((param) => (
                                    <div key={param.name}>
                                        <label className="block text-xs text-gray-500 mb-1">{param.description}</label>
                                        <input
                                            type="number"
                                            value={parameters[param.name] || param.defaultValue}
                                            onChange={(e) =>
                                                setParameters({ ...parameters, [param.name]: parseInt(e.target.value) })
                                            }
                                            min={param.minValue}
                                            max={param.maxValue}
                                            className="input text-sm"
                                        />
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Run Button */}
                    <div className="mt-6 flex items-center gap-4">
                        <button onClick={runBacktest} disabled={loading} className="btn-primary flex items-center gap-2">
                            {loading ? (
                                <>
                                    <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                                    Running...
                                </>
                            ) : (
                                <>
                                    <Play className="w-5 h-5" />
                                    Run Backtest
                                </>
                            )}
                        </button>
                        {error && <p className="text-accent-danger text-sm">{error}</p>}
                    </div>
                </section>

                {/* Results */}
                {result && (
                    <>
                        {/* Strategy Summary */}
                        <section className="card p-6 mb-8 animate-fade-in">
                            <StrategySummary
                                strategyName={strategies.find(s => s.id === result.strategy)?.name || result.strategy}
                                symbol={result.symbol}
                                metrics={result.metrics}
                                dateRange={{ start: startDate, end: endDate }}
                            />
                        </section>

                        {/* Metrics Grid */}
                        <section className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4 mb-8 animate-slide-up">
                            <MetricCard
                                label="Total Return"
                                value={`${(result.metrics.totalReturn * 100).toFixed(2)}%`}
                                icon={<DollarSign className="w-5 h-5" />}
                                trend={result.metrics.totalReturn >= 0 ? 'up' : 'down'}
                            />
                            <MetricCard
                                label="Annual Return"
                                value={`${(result.metrics.annualizedReturn * 100).toFixed(2)}%`}
                                icon={<TrendingUp className="w-5 h-5" />}
                                trend={result.metrics.annualizedReturn >= 0 ? 'up' : 'down'}
                            />
                            <MetricCard
                                label="Max Drawdown"
                                value={`${(result.metrics.maxDrawdownPercent * 100).toFixed(2)}%`}
                                icon={<TrendingDown className="w-5 h-5" />}
                                trend="down"
                            />
                            <MetricCard
                                label="Sharpe Ratio"
                                value={result.metrics.sharpeRatio.toFixed(3)}
                                icon={<Activity className="w-5 h-5" />}
                                trend={result.metrics.sharpeRatio >= 1 ? 'up' : 'neutral'}
                            />
                            <MetricCard
                                label="Win Rate"
                                value={`${(result.metrics.winRate * 100).toFixed(1)}%`}
                                icon={<Target className="w-5 h-5" />}
                                trend={result.metrics.winRate >= 0.5 ? 'up' : 'down'}
                            />
                            <MetricCard
                                label="Total Trades"
                                value={result.metrics.totalTrades.toString()}
                                icon={<Award className="w-5 h-5" />}
                                trend="neutral"
                            />
                        </section>

                        {/* Equity Curve */}
                        <section className="card p-6 mb-8 animate-slide-up" style={{ animationDelay: '0.05s' }}>
                            <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
                                <TrendingUp className="w-5 h-5 text-accent-primary" />
                                Portfolio Equity Curve
                            </h2>
                            <EquityCurve data={result.equityCurve || []} initialCapital={100000} />
                        </section>

                        {/* Chart */}
                        <section className="card p-6 mb-8 animate-slide-up" style={{ animationDelay: '0.1s' }}>
                            <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
                                <BarChart3 className="w-5 h-5 text-accent-primary" />
                                Price Chart & Trade Signals
                            </h2>
                            <TradingChart candles={result.candles} trades={result.trades} />
                        </section>

                        {/* Trade List */}
                        <section className="card p-6 animate-slide-up" style={{ animationDelay: '0.2s' }}>
                            <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
                                <Calendar className="w-5 h-5 text-accent-primary" />
                                Trade History
                            </h2>
                            <TradeList trades={result.trades} />
                        </section>
                    </>
                )}

                {/* Empty State */}
                {!result && !loading && (
                    <div className="card p-12 text-center animate-fade-in mb-8">
                        <div className="w-16 h-16 mx-auto mb-6 rounded-2xl bg-dark-700 flex items-center justify-center">
                            <BarChart3 className="w-8 h-8 text-gray-500" />
                        </div>
                        <h3 className="text-xl font-semibold mb-2">No Backtest Results Yet</h3>
                        <p className="text-gray-400 max-w-md mx-auto">
                            Configure your strategy parameters above and click "Run Backtest" to analyze historical performance.
                        </p>
                    </div>
                )}

                {/* Community History — always visible */}
                <BacktestHistory />
            </main>
        </div>
    );
}
