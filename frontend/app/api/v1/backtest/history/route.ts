import { NextResponse } from 'next/server';
import { getSupabase } from '@/lib/supabase';

export const dynamic = 'force-dynamic';

export async function GET() {
    const supabase = getSupabase();
    const { data, error } = await supabase
        .from('backtest_results')
        .select('id, created_at, symbol, strategy, start_date, end_date, parameters, metrics')
        .order('created_at', { ascending: false })
        .limit(20);

    if (error) return NextResponse.json({ message: error.message }, { status: 500 });
    return NextResponse.json(data ?? []);
}
