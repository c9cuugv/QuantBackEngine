import { NextResponse } from 'next/server';
import { createClient } from '@supabase/supabase-js';

const supabase = createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
);

export async function GET() {
    const { data, error } = await supabase
        .from('backtest_results')
        .select('id, created_at, symbol, strategy, start_date, end_date, parameters, metrics')
        .order('created_at', { ascending: false })
        .limit(20);

    if (error) return NextResponse.json({ message: error.message }, { status: 500 });
    return NextResponse.json(data ?? []);
}
