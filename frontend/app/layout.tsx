import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
    title: 'QuantBackEngine | Algorithmic Trading Backtest Platform',
    description: 'Professional-grade backtesting engine for quantitative trading strategies. Analyze, optimize, and validate your trading algorithms.',
    keywords: ['backtesting', 'algorithmic trading', 'quantitative finance', 'trading strategies'],
};

export default function RootLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    return (
        <html lang="en" className="dark">
            <body className="antialiased">{children}</body>
        </html>
    );
}
