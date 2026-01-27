import { TrendingUp, TrendingDown, AlertTriangle, CheckCircle, Info } from 'lucide-react';

interface StrategySummaryProps {
    strategyName: string;
    symbol: string;
    metrics: {
        totalReturn: number;
        annualizedReturn: number;
        maxDrawdownPercent: number;
        sharpeRatio: number;
        winRate: number;
        totalTrades: number;
    };
    dateRange: {
        start: string;
        end: string;
    };
}

export default function StrategySummary({ strategyName, symbol, metrics, dateRange }: StrategySummaryProps) {
    // Determine rating based on metrics
    const getRating = () => {
        let score = 0;

        if (metrics.totalReturn > 0.5) score += 2;
        else if (metrics.totalReturn > 0.2) score += 1;
        else if (metrics.totalReturn < 0) score -= 1;

        if (metrics.sharpeRatio > 1.5) score += 2;
        else if (metrics.sharpeRatio > 1) score += 1;
        else if (metrics.sharpeRatio < 0.5) score -= 1;

        if (metrics.maxDrawdownPercent < 0.15) score += 1;
        else if (metrics.maxDrawdownPercent > 0.3) score -= 1;

        if (metrics.winRate > 0.6) score += 1;
        else if (metrics.winRate < 0.4) score -= 1;

        if (score >= 4) return { label: 'Excellent', color: 'text-accent-success', bg: 'bg-accent-success/10' };
        if (score >= 2) return { label: 'Good', color: 'text-green-400', bg: 'bg-green-400/10' };
        if (score >= 0) return { label: 'Moderate', color: 'text-yellow-400', bg: 'bg-yellow-400/10' };
        return { label: 'Poor', color: 'text-accent-danger', bg: 'bg-accent-danger/10' };
    };

    const rating = getRating();

    // Generate insights
    const insights = [];

    if (metrics.sharpeRatio > 1) {
        insights.push({
            type: 'positive',
            text: `Strong risk-adjusted returns with Sharpe ratio of ${metrics.sharpeRatio.toFixed(2)}`
        });
    } else if (metrics.sharpeRatio < 0.5) {
        insights.push({
            type: 'warning',
            text: 'Low Sharpe ratio suggests poor risk-adjusted returns'
        });
    }

    if (metrics.maxDrawdownPercent > 0.25) {
        insights.push({
            type: 'warning',
            text: `High max drawdown of ${(metrics.maxDrawdownPercent * 100).toFixed(1)}% may indicate volatility risk`
        });
    }

    if (metrics.winRate > 0.6 && metrics.totalTrades > 2) {
        insights.push({
            type: 'positive',
            text: `${(metrics.winRate * 100).toFixed(0)}% win rate over ${metrics.totalTrades} trades`
        });
    }

    if (metrics.annualizedReturn > 0.15) {
        insights.push({
            type: 'positive',
            text: `Outperformed typical market returns with ${(metrics.annualizedReturn * 100).toFixed(1)}% annual`
        });
    }

    return (
        <div className="flex flex-col lg:flex-row gap-6">
            {/* Main Summary */}
            <div className="flex-1">
                <div className="flex items-center gap-3 mb-4">
                    <div className={`px-3 py-1 rounded-full text-sm font-medium ${rating.color} ${rating.bg}`}>
                        {rating.label} Performance
                    </div>
                </div>

                <h3 className="text-lg font-semibold mb-2">
                    {strategyName} on {symbol}
                </h3>
                <p className="text-gray-400 text-sm mb-4">
                    Backtest period: {new Date(dateRange.start).toLocaleDateString()} - {new Date(dateRange.end).toLocaleDateString()}
                </p>

                {/* Quick Stats Row */}
                <div className="flex flex-wrap gap-4 text-sm">
                    <div className="flex items-center gap-2">
                        {metrics.totalReturn >= 0 ? (
                            <TrendingUp className="w-4 h-4 text-accent-success" />
                        ) : (
                            <TrendingDown className="w-4 h-4 text-accent-danger" />
                        )}
                        <span className={metrics.totalReturn >= 0 ? 'text-accent-success' : 'text-accent-danger'}>
                            {(metrics.totalReturn * 100).toFixed(2)}% Total Return
                        </span>
                    </div>
                    <div className="text-gray-500">|</div>
                    <div className="text-gray-300">
                        ${(100000 * (1 + metrics.totalReturn)).toLocaleString(undefined, { maximumFractionDigits: 0 })} final value
                    </div>
                </div>
            </div>

            {/* Insights Panel */}
            {insights.length > 0 && (
                <div className="lg:w-80 border-t lg:border-t-0 lg:border-l border-white/5 pt-4 lg:pt-0 lg:pl-6">
                    <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
                        <Info className="w-4 h-4" />
                        Key Insights
                    </div>
                    <ul className="space-y-2">
                        {insights.map((insight, idx) => (
                            <li key={idx} className="flex items-start gap-2 text-sm">
                                {insight.type === 'positive' ? (
                                    <CheckCircle className="w-4 h-4 text-accent-success flex-shrink-0 mt-0.5" />
                                ) : (
                                    <AlertTriangle className="w-4 h-4 text-yellow-400 flex-shrink-0 mt-0.5" />
                                )}
                                <span className="text-gray-300">{insight.text}</span>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}
