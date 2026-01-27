'use client';

import { useEffect, useRef, useId } from 'react';
import { createChart, ColorType, IChartApi, ISeriesApi, CandlestickData, Time } from 'lightweight-charts';

interface Candle {
    time: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
}

interface Trade {
    type: string;
    entryDate: string;
    entryPrice: number;
    exitDate: string;
    exitPrice: number;
    pnl: number;
}

interface TradingChartProps {
    candles: Candle[];
    trades: Trade[];
}

export default function TradingChart({ candles, trades }: TradingChartProps) {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);

    // Create chart on mount
    useEffect(() => {
        if (!chartContainerRef.current) return;

        // Clean up any existing chart first
        if (chartRef.current) {
            chartRef.current.remove();
            chartRef.current = null;
            seriesRef.current = null;
        }

        const chart = createChart(chartContainerRef.current, {
            width: chartContainerRef.current.clientWidth,
            height: 400,
            layout: {
                background: { type: ColorType.Solid, color: 'transparent' },
                textColor: '#9ca3af',
            },
            grid: {
                vertLines: { color: 'rgba(255, 255, 255, 0.03)' },
                horzLines: { color: 'rgba(255, 255, 255, 0.03)' },
            },
            crosshair: {
                mode: 1,
                vertLine: {
                    color: 'rgba(99, 102, 241, 0.5)',
                    labelBackgroundColor: '#6366f1',
                },
                horzLine: {
                    color: 'rgba(99, 102, 241, 0.5)',
                    labelBackgroundColor: '#6366f1',
                },
            },
            rightPriceScale: {
                borderColor: 'rgba(255, 255, 255, 0.1)',
            },
            timeScale: {
                borderColor: 'rgba(255, 255, 255, 0.1)',
                timeVisible: true,
                secondsVisible: false,
            },
            handleScroll: {
                vertTouchDrag: false,
            },
        });

        chartRef.current = chart;

        // Add candlestick series
        const candlestickSeries = chart.addCandlestickSeries({
            upColor: '#22c55e',
            downColor: '#ef4444',
            borderDownColor: '#ef4444',
            borderUpColor: '#22c55e',
            wickDownColor: '#ef4444',
            wickUpColor: '#22c55e',
        });

        seriesRef.current = candlestickSeries;

        // Handle resize
        const handleResize = () => {
            if (chartContainerRef.current && chartRef.current) {
                chartRef.current.applyOptions({ width: chartContainerRef.current.clientWidth });
            }
        };

        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            if (chartRef.current) {
                chartRef.current.remove();
                chartRef.current = null;
                seriesRef.current = null;
            }
        };
    }, []); // Only run once on mount

    // Update data when candles or trades change
    useEffect(() => {
        if (!seriesRef.current || !chartRef.current || candles.length === 0) return;

        // Format data for Lightweight Charts
        const formattedCandles = candles.map((c) => ({
            time: c.time as Time,
            open: c.open,
            high: c.high,
            low: c.low,
            close: c.close,
        }));

        // Set the new data
        seriesRef.current.setData(formattedCandles);

        // Add trade markers
        const markers = trades.flatMap((trade) => {
            const entryTime = new Date(trade.entryDate).getTime() / 1000;
            const exitTime = new Date(trade.exitDate).getTime() / 1000;

            return [
                {
                    time: entryTime as Time,
                    position: 'belowBar' as const,
                    color: '#22c55e',
                    shape: 'arrowUp' as const,
                    text: 'BUY',
                },
                {
                    time: exitTime as Time,
                    position: 'aboveBar' as const,
                    color: trade.pnl >= 0 ? '#22c55e' : '#ef4444',
                    shape: 'arrowDown' as const,
                    text: trade.pnl >= 0 ? 'SELL +' : 'SELL -',
                },
            ];
        });

        if (markers.length > 0) {
            seriesRef.current.setMarkers(markers);
        } else {
            seriesRef.current.setMarkers([]);
        }

        // Fit content to show all data
        chartRef.current.timeScale().fitContent();

    }, [candles, trades]);

    return (
        <div
            ref={chartContainerRef}
            className="w-full h-[400px] rounded-xl overflow-hidden"
        />
    );
}
