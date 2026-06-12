'use client';

import { useState, useEffect, useCallback } from 'react';
import { Brain, TrendingUp, TrendingDown, Loader2, RefreshCw } from 'lucide-react';

interface Importance { name: string; weight: number; importance: number; }

interface MLResult {
    symbol: string;
    strategy: string;
    trainSamples: number;
    testSamples: number;
    trainAccuracy: number;
    testAccuracy: number;
    ruleBasedAccuracy: number;
    mlEdge: number;
    currentPrediction: { prob: number; label: number } | null;
    importances: Importance[];
}

interface Props {
    symbol: string;
    strategy: string;
    parameters: Record<string, number>;
    strategyName: string;
}

function pct(n: number) { return (n * 100).toFixed(1) + '%'; }

export default function MLPredictor({ symbol, strategy, parameters, strategyName }: Props) {
    const [result, setResult] = useState<MLResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const run = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const res = await fetch('/api/v1/ml/predict', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ symbol, strategy, parameters }),
            });
            if (!res.ok) { const d = await res.json(); throw new Error(d.message); }
            setResult(await res.json());
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Failed');
        } finally {
            setLoading(false);
        }
    }, [symbol, strategy, parameters]);

    // Auto-run when symbol/strategy changes
    useEffect(() => { run(); }, [run]);

    const pred = result?.currentPrediction;
    const predUp = pred && pred.label === 1;
    const mlWins = result && result.mlEdge > 0.005;

    return (
        <section className="card p-6 animate-fade-in">
            <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-3">
                    <Brain className="w-5 h-5 text-accent-primary" />
                    <h2 className="text-lg font-semibold">ML Predictor</h2>
                    <span className="text-xs text-gray-500 bg-dark-700 px-2 py-0.5 rounded-full">logistic regression</span>
                </div>
                <button onClick={run} disabled={loading} className="text-gray-500 hover:text-white transition-colors">
                    <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                </button>
            </div>

            {loading && !result ? (
                <div className="flex items-center justify-center py-10 text-gray-500 gap-2">
                    <Loader2 className="w-5 h-5 animate-spin" />
                    Training on 3 years of {symbol} data...
                </div>
            ) : error ? (
                <p className="text-accent-danger text-sm py-4">{error}</p>
            ) : result ? (
                <>
                    {/* Current prediction */}
                    {pred && (
                        <div className={`rounded-xl border p-4 mb-6 flex items-center justify-between ${
                            predUp
                                ? 'bg-accent-success/5 border-accent-success/20'
                                : 'bg-accent-danger/5 border-accent-danger/20'
                        }`}>
                            <div className="flex items-center gap-3">
                                {predUp
                                    ? <TrendingUp className="w-6 h-6 text-accent-success" />
                                    : <TrendingDown className="w-6 h-6 text-accent-danger" />}
                                <div>
                                    <p className={`text-lg font-bold ${predUp ? 'text-accent-success' : 'text-accent-danger'}`}>
                                        {predUp ? 'BULLISH' : 'BEARISH'} — {pct(predUp ? pred.prob : 1 - pred.prob)} confidence
                                    </p>
                                    <p className="text-xs text-gray-500">{symbol} next-day directional prediction</p>
                                </div>
                            </div>
                            {/* Probability bar */}
                            <div className="hidden md:block w-32">
                                <div className="h-2 bg-dark-600 rounded-full overflow-hidden">
                                    <div
                                        className={`h-full rounded-full transition-all ${predUp ? 'bg-accent-success' : 'bg-accent-danger'}`}
                                        style={{ width: `${pred.prob * 100}%` }}
                                    />
                                </div>
                                <div className="flex justify-between text-xs text-gray-600 mt-1">
                                    <span>Bear</span><span>Bull</span>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Accuracy comparison */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                        <div className="bg-dark-700 rounded-xl p-4">
                            <p className="text-xs text-gray-500 mb-1">ML Test Accuracy</p>
                            <p className="text-2xl font-bold text-white">{pct(result.testAccuracy)}</p>
                            <p className="text-xs text-gray-600 mt-1">{result.testSamples} test days</p>
                        </div>
                        <div className="bg-dark-700 rounded-xl p-4">
                            <p className="text-xs text-gray-500 mb-1">{strategyName} Accuracy</p>
                            <p className="text-2xl font-bold text-white">
                                {result.ruleBasedAccuracy > 0 ? pct(result.ruleBasedAccuracy) : 'No signals'}
                            </p>
                            <p className="text-xs text-gray-600 mt-1">same test period</p>
                        </div>
                        <div className={`rounded-xl p-4 border ${
                            mlWins
                                ? 'bg-accent-success/5 border-accent-success/20'
                                : 'bg-accent-danger/5 border-accent-danger/20'
                        }`}>
                            <p className="text-xs text-gray-500 mb-1">ML Edge</p>
                            <p className={`text-2xl font-bold ${mlWins ? 'text-accent-success' : 'text-accent-danger'}`}>
                                {result.mlEdge >= 0 ? '+' : ''}{pct(result.mlEdge)}
                            </p>
                            <p className="text-xs text-gray-600 mt-1">{mlWins ? 'ML wins' : 'rule-based wins'}</p>
                        </div>
                    </div>

                    {/* Overfitting check */}
                    <div className="flex items-center gap-3 mb-6 text-xs text-gray-500">
                        <span>Train accuracy: <span className="text-white">{pct(result.trainAccuracy)}</span></span>
                        <span>·</span>
                        <span>Overfit gap: <span className={Math.abs(result.trainAccuracy - result.testAccuracy) > 0.05 ? 'text-yellow-500' : 'text-accent-success'}>
                            {pct(Math.abs(result.trainAccuracy - result.testAccuracy))}
                        </span></span>
                        <span>·</span>
                        <span>Trained on {result.trainSamples} samples</span>
                    </div>

                    {/* Feature importances */}
                    <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">Feature Importances</p>
                    <div className="space-y-2">
                        {result.importances.map(f => (
                            <div key={f.name} className="flex items-center gap-3">
                                <span className="text-xs font-mono text-gray-400 w-20 shrink-0">{f.name}</span>
                                <div className="flex-1 h-2 bg-dark-600 rounded-full overflow-hidden">
                                    <div
                                        className={`h-full rounded-full ${f.weight >= 0 ? 'bg-accent-success' : 'bg-accent-danger'}`}
                                        style={{ width: `${f.importance * 100}%` }}
                                    />
                                </div>
                                <span className={`text-xs font-mono w-12 text-right ${f.weight >= 0 ? 'text-accent-success' : 'text-accent-danger'}`}>
                                    {f.weight >= 0 ? '+' : ''}{f.weight.toFixed(3)}
                                </span>
                            </div>
                        ))}
                    </div>
                    <p className="text-xs text-gray-600 mt-3">
                        Green = bullish signal · Red = bearish signal · Bar length = relative importance
                    </p>
                </>
            ) : null}
        </section>
    );
}
