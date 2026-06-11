import { NextResponse } from 'next/server';

const STRATEGIES = [
    {
        id: 'SMA_CROSSOVER',
        name: 'SMA Crossover',
        description: 'Trend-following strategy using simple moving average crossovers',
        parameters: [
            { name: 'fastPeriod', type: 'INTEGER', defaultValue: 20, minValue: 5, maxValue: 50, description: 'Fast SMA period' },
            { name: 'slowPeriod', type: 'INTEGER', defaultValue: 50, minValue: 20, maxValue: 200, description: 'Slow SMA period' },
        ],
    },
    {
        id: 'EMA_CROSSOVER',
        name: 'EMA Crossover',
        description: 'Trend-following strategy using exponential moving average crossovers',
        parameters: [
            { name: 'fastPeriod', type: 'INTEGER', defaultValue: 12, minValue: 5, maxValue: 50, description: 'Fast EMA period' },
            { name: 'slowPeriod', type: 'INTEGER', defaultValue: 26, minValue: 20, maxValue: 200, description: 'Slow EMA period' },
        ],
    },
    {
        id: 'RSI_OVERSOLD',
        name: 'RSI Oversold/Overbought',
        description: 'Mean-reversion strategy using the Relative Strength Index',
        parameters: [
            { name: 'period', type: 'INTEGER', defaultValue: 14, minValue: 5, maxValue: 30, description: 'RSI period' },
            { name: 'oversoldLevel', type: 'INTEGER', defaultValue: 30, minValue: 10, maxValue: 40, description: 'Oversold level' },
            { name: 'overboughtLevel', type: 'INTEGER', defaultValue: 70, minValue: 60, maxValue: 90, description: 'Overbought level' },
        ],
    },
    {
        id: 'BOLLINGER_BANDS',
        name: 'Bollinger Bands',
        description: 'Mean-reversion strategy using Bollinger Bands breakouts',
        parameters: [
            { name: 'period', type: 'INTEGER', defaultValue: 20, minValue: 10, maxValue: 50, description: 'Bollinger period' },
            { name: 'stdDev', type: 'DOUBLE', defaultValue: 2, minValue: 1, maxValue: 3, description: 'Standard deviations' },
        ],
    },
];

export async function GET() {
    return NextResponse.json(STRATEGIES);
}
