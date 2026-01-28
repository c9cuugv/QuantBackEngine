import { ReactNode } from 'react';

interface MetricCardProps {
    label: string;
    value: string;
    icon: ReactNode;
    trend?: 'up' | 'down' | 'neutral';
}

export default function MetricCard({ label, value, icon, trend = 'neutral' }: MetricCardProps) {
    const trendColors = {
        up: 'text-accent-success',
        down: 'text-accent-danger',
        neutral: 'text-gray-300',
    };

    const trendBg = {
        up: 'bg-accent-success/10',
        down: 'bg-accent-danger/10',
        neutral: 'bg-dark-600',
    };

    return (
        <div className="metric-card card-hover group">
            <div className="flex items-center justify-between">
                <div className={`p-2 rounded-lg ${trendBg[trend]} transition-colors`}>
                    <span className={`${trendColors[trend]}`}>{icon}</span>
                </div>
            </div>
            <div className={`metric-value ${trendColors[trend]}`}>{value}</div>
            <div className="metric-label">{label}</div>
        </div>
    );
}
