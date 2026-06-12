import { test, expect, request } from '@playwright/test';

// Backend is only reachable via nginx (port 80); port 8080 is internal to Docker network.
const BACKEND = process.env.BACKEND_URL || 'http://localhost';

test.describe('Backend API — Baseline', () => {
  test('GET /api/v1/backtest/strategies returns non-empty list', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.get('/api/v1/backtest/strategies');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
    expect(body.length).toBeGreaterThan(0);
    const strategy = body[0];
    expect(strategy).toHaveProperty('id');
    expect(strategy).toHaveProperty('name');
    expect(strategy).toHaveProperty('parameters');
    await ctx.dispose();
  });

  test('GET /api/v1/backtest/strategies/{id} returns strategy detail', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const listRes = await ctx.get('/api/v1/backtest/strategies');
    expect(listRes.status()).toBe(200);
    const strategies = await listRes.json();
    const firstId: string = strategies[0].id;

    const detailRes = await ctx.get(`/api/v1/backtest/strategies/${firstId}`);
    expect(detailRes.status()).toBe(200);
    const detail = await detailRes.json();
    expect(detail.id).toBe(firstId);
    await ctx.dispose();
  });

  test('GET /api/v1/backtest/strategies/{id} returns 404 for unknown id', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.get('/api/v1/backtest/strategies/NON_EXISTENT_STRATEGY_XYZ');
    expect(res.status()).toBe(404);
    await ctx.dispose();
  });

  test('GET /api/v1/market-data/symbols returns list', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.get('/api/v1/market-data/symbols');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
    await ctx.dispose();
  });

  test('GET /api/v1/market-data/sources returns sources list', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.get('/api/v1/market-data/sources');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
    await ctx.dispose();
  });

  test('POST /api/v1/backtest/run with SMA_CROSSOVER returns metrics', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.post('/api/v1/backtest/run', {
      data: {
        symbol: 'AAPL',
        strategy: 'SMA_CROSSOVER',
        startDate: '2022-01-01',
        endDate: '2023-01-01',
        initialCapital: 10000,
        parameters: {},
      },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('metrics');
    expect(body.metrics).toHaveProperty('totalReturn');
    expect(body.metrics).toHaveProperty('sharpeRatio');
    expect(body.metrics).toHaveProperty('maxDrawdown');
    await ctx.dispose();
  });

  test('POST /api/v1/backtest/run with invalid payload returns 400', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.post('/api/v1/backtest/run', {
      data: {},
    });
    expect(res.status()).toBe(400);
    await ctx.dispose();
  });

  test('Backend health: strategies endpoint returns 200', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.get('/api/v1/backtest/strategies');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
    expect(body.length).toBeGreaterThan(0);
    await ctx.dispose();
  });

  test('GET /api/v1/market-data/python/{symbol} returns 200 or 400 (bridge-dependent)', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.get('/api/v1/market-data/python/AAPL?source=yfinance&start=2023-01-01&end=2023-03-31');
    // 200 = bridge available and returned data; 400 = bridge unavailable or no data — both are valid
    expect([200, 400]).toContain(res.status());
    await ctx.dispose();
  });

  test('POST /api/v1/analytics/quantstats always returns 200 with success field', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.post('/api/v1/analytics/quantstats', {
      data: { action: 'ping' },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('success');
    await ctx.dispose();
  });

  // Next.js-only routes (nginx longest-prefix routes these to frontend:3000, not Spring Boot)
  test('GET /api/v1/backtest/history returns array', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.get('/api/v1/backtest/history');
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
    await ctx.dispose();
  });

  test('POST /api/v1/paper/portfolio missing required fields returns 400', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.post('/api/v1/paper/portfolio', {
      data: { symbol: 'AAPL' }, // missing sessionId and strategy
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body).toHaveProperty('message');
    await ctx.dispose();
  });

  test('POST /api/v1/paper/portfolio valid payload returns portfolio state', async () => {
    const ctx = await request.newContext({ baseURL: BACKEND });
    const res = await ctx.post('/api/v1/paper/portfolio', {
      data: {
        sessionId: '00000000-e2e0-4000-8000-000000000001', // fixed UUID for e2e
        symbol: 'AAPL',
        strategy: 'SMA_CROSSOVER',
        parameters: {},
      },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('currentValue');
    expect(body).toHaveProperty('currentSignal');
    expect(['BUY', 'SELL', 'HOLD']).toContain(body.currentSignal);
    await ctx.dispose();
  });

});
