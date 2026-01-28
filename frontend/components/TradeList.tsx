interface Trade {
    type: string;
    entryDate: string;
    entryPrice: number;
    exitDate: string;
    exitPrice: number;
    shares: number;
    pnl: number;
    commission: number;
}

interface TradeListProps {
    trades: Trade[];
}

export default function TradeList({ trades }: TradeListProps) {
    const formatDate = (dateStr: string) => {
        return new Date(dateStr).toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
        });
    };

    const formatCurrency = (val: number) => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
        }).format(val);
    };

    if (trades.length === 0) {
        return (
            <div className="text-center py-8 text-gray-500">
                No trades executed during this backtest period.
            </div>
        );
    }

    return (
        <div className="overflow-x-auto">
            <table className="w-full text-sm">
                <thead>
                    <tr className="text-left text-gray-500 border-b border-white/5">
                        <th className="pb-3 font-medium">#</th>
                        <th className="pb-3 font-medium">Entry Date</th>
                        <th className="pb-3 font-medium">Entry Price</th>
                        <th className="pb-3 font-medium">Exit Date</th>
                        <th className="pb-3 font-medium">Exit Price</th>
                        <th className="pb-3 font-medium">Shares</th>
                        <th className="pb-3 font-medium">P&L</th>
                        <th className="pb-3 font-medium">Commission</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                    {trades.map((trade, idx) => (
                        <tr key={idx} className="hover:bg-white/[0.02] transition-colors">
                            <td className="py-3 text-gray-400">{idx + 1}</td>
                            <td className="py-3">{formatDate(trade.entryDate)}</td>
                            <td className="py-3 font-mono">{formatCurrency(trade.entryPrice)}</td>
                            <td className="py-3">{formatDate(trade.exitDate)}</td>
                            <td className="py-3 font-mono">{formatCurrency(trade.exitPrice)}</td>
                            <td className="py-3 font-mono">{trade.shares.toFixed(2)}</td>
                            <td className={`py-3 font-mono font-semibold ${trade.pnl >= 0 ? 'text-accent-success' : 'text-accent-danger'}`}>
                                {trade.pnl >= 0 ? '+' : ''}{formatCurrency(trade.pnl)}
                            </td>
                            <td className="py-3 font-mono text-gray-500">{formatCurrency(trade.commission)}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
