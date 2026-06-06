import { test, expect } from '@playwright/test';

test.describe('Dashboard — Baseline UI', () => {
  test.beforeEach(async ({ page }) => {
    // 60s covers transient nginx→frontend latency after prolonged test runs
    await page.goto('/', { timeout: 60000 });
    await page.waitForLoadState('networkidle');
  });

  test('page loads and shows QuantBackEngine heading', async ({ page }) => {
    // title: 'QuantBackEngine | Algorithmic Trading Backtest Platform'
    await expect(page).toHaveTitle(/QuantBackEngine|backtest/i);
    await page.screenshot({ path: 'artifacts/dashboard-load.png', fullPage: true });
  });

  test('symbol selector pre-filled with AAPL', async ({ page }) => {
    // Symbol is a <select> element (not an input); first select on page is symbol
    const symbolSelect = page.locator('select').first();
    await expect(symbolSelect).toBeVisible();
    await expect(symbolSelect).toHaveValue('AAPL');
  });

  test('strategy selector visible', async ({ page }) => {
    // Strategy is the second <select> on the page
    const strategySelect = page.locator('select').nth(1);
    await expect(strategySelect).toBeVisible();
    await expect(strategySelect).toHaveValue('SMA_CROSSOVER');
  });

  test('run backtest button is present', async ({ page }) => {
    // Button text is "Run Backtest" (className="btn-primary")
    const runBtn = page.locator('button:has-text("Run Backtest")');
    await expect(runBtn).toBeVisible();
  });

  test('metrics cards render after backtest', async ({ page }) => {
    // Symbol select defaults to AAPL — just trigger run
    const runBtn = page.locator('button:has-text("Run Backtest")');
    await runBtn.click();

    // Wait for the backtest API response
    await page.waitForResponse(
      resp => resp.url().includes('/api/') && resp.status() === 200,
      { timeout: 30000 }
    );

    // Metric values are rendered inside elements with class containing "metric" or "card"
    const cards = page.locator('[data-testid="metric-card"], .metric-card, [class*="metric"], [class*="card"]');
    await expect(cards.first()).toBeVisible({ timeout: 15000 });

    await page.screenshot({ path: 'artifacts/dashboard-results.png', fullPage: true });
  });
});
