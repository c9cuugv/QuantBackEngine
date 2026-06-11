import { NextResponse } from 'next/server';

const SYMBOLS = [
    'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'META', 'TSLA', 'NVDA', 'BRK-B',
    'JPM', 'V', 'WMT', 'XOM', 'JNJ', 'PG', 'MA', 'UNH', 'HD', 'CVX',
    'MRK', 'LLY', 'ABBV', 'KO', 'PEP', 'AVGO', 'COST', 'MCD', 'CSCO',
    'ACN', 'TMO', 'BAC', 'ABT', 'CRM', 'NFLX', 'AMD', 'INTC', 'ORCL',
    'QCOM', 'TXN', 'IBM', 'ADBE', 'NOW', 'INTU', 'AMAT', 'MU', 'LRCX',
    'SPY', 'QQQ', 'IWM', 'DIA', 'GLD', 'SLV', 'USO',
    'BTC-USD', 'ETH-USD',
];

export async function GET() {
    return NextResponse.json(SYMBOLS);
}
