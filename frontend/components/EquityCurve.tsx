'use client';

import { useEffect, useRef } from 'react';
import { createChart, ColorType, IChartApi, ISeriesApi, Time, LineData } from 'lightweight-charts';

interface EquityPoint {
    timestamp: number;
    value: number;
}

interface EquityCurveProps {
    data: EquityPoint[];
    initialCapital?: number;
}

export default function EquityCurve({ data, initialCapital = 100000 }: EquityCurveProps) {
    const chartContainerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<IChartApi | null>(null);
    const seriesRef = useRef<ISeriesApi<'Area'> | null>(null);

    useEffect(() => {
        if (!chartContainerRef.current) return;

        // Clean up existing chart
        if (chartRef.current) {
            chartRef.current.remove();
            chartRef.current = null;
            seriesRef.current = null;
        }

        const chart = createChart(chartContainerRef.current, {
            width: chartContainerRef.current.clientWidth,
            height: 300,
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
        });

        chartRef.current = chart;

        // Sample data to reduce points if needed for smooth rendering
        const maxPoints = 500;
        const sampledData = data.length > maxPoints
            ? data.filter((_, idx) => idx % Math.ceil(data.length / maxPoints) === 0)
            : data;

        // Determine color based on P/L
        const finalValue = sampledData.length > 0 ? sampledData[sampledData.length - 1].value : initialCapital;
        const isProfit = finalValue >= initialCapital;

        const areaSeries = chart.addAreaSeries({
            lineColor: isProfit ? '#22c55e' : '#ef4444',
            topColor: isProfit ? 'rgba(34, 197, 94, 0.4)' : 'rgba(239, 68, 68, 0.4)',
            bottomColor: isProfit ? 'rgba(34, 197, 94, 0.05)' : 'rgba(239, 68, 68, 0.05)',
            lineWidth: 2,
            priceFormat: {
                type: 'custom',
                formatter: (price: number) => `$${(price / 1000).toFixed(1)}k`,
            },
        });

        seriesRef.current = areaSeries;

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
    }, []);

    // Update data separately
    useEffect(() => {
        if (!seriesRef.current || !chartRef.current || data.length === 0) return;

        // Sample data
        const maxPoints = 500;
        const sampledData = data.length > maxPoints
            ? data.filter((_, idx) => idx % Math.ceil(data.length / maxPoints) === 0)
            : data;

        const formattedData: LineData[] = sampledData.map((point) => ({
            time: Math.floor(point.timestamp / 1000) as Time,
            value: point.value,
        }));

        seriesRef.current.setData(formattedData);
        chartRef.current.timeScale().fitContent();
    }, [data]);

    // Calculate summary stats
    const startValue = data.length > 0 ? data[0].value : initialCapital;
    const endValue = data.length > 0 ? data[data.length - 1].value : initialCapital;
    const totalGain = endValue - startValue;
    const percentChange = ((endValue - startValue) / startValue) * 100;
    const isProfit = totalGain >= 0;

    return (
        <div>
            {/* Mini Stats Row */}
            <div className="flex items-center gap-6 mb-4 text-sm">
                <div className="flex items-center gap-2">
                    <span className="text-gray-500">Start:</span>
                    <span className="font-mono font-medium">
                        ${startValue.toLocaleString(undefined, { maximumFractionDigits: 0 })}
                    </span>
                </div>
                <div className="w-px h-4 bg-white/10" />
                <div className="flex items-center gap-2">
                    <span className="text-gray-500">End:</span>
                    <span className="font-mono font-medium">
                        ${endValue.toLocaleString(undefined, { maximumFractionDigits: 0 })}
                    </span>
                </div>
                <div className="w-px h-4 bg-white/10" />
                <div className="flex items-center gap-2">
                    <span className="text-gray-500">P/L:</span>
                    <span className={`font-mono font-semibold ${isProfit ? 'text-accent-success' : 'text-accent-danger'}`}>
                        {isProfit ? '+' : ''}{totalGain.toLocaleString(undefined, { maximumFractionDigits: 0 })}
                        <span className="text-xs ml-1">
                            ({isProfit ? '+' : ''}{percentChange.toFixed(2)}%)
                        </span>
                    </span>
                </div>
            </div>

            <div
                ref={chartContainerRef}
                className="w-full h-[300px] rounded-xl overflow-hidden"
            />
        </div>
    );
}
