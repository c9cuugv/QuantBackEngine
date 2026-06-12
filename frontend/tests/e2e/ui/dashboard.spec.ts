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

  test('symbol input pre-filled with AAPL and accepts free text', async ({ page }) => {
    // Symbol is a free-text input near the "Stock Symbol" label
    const symbolInput = page.locator('input[placeholder*="AAPL"], input[placeholder*="ticker"]').first();
    await expect(symbolInput).toBeVisible();
    await expect(symbolInput).toHaveValue('AAPL');

    await symbolInput.fill('amd');
    await expect(symbolInput).toHaveValue('AMD'); // auto-uppercased via onChange
  });

  test('strategy selector visible', async ({ page }) => {
    // Strategy is the only <select> on the page
    const strategySelect = page.locator('select').first();
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

  test('Paper Portfolio section renders on load', async ({ page }) => {
    // PaperTrading component is always visible (not conditional on backtest result)
    const heading = page.locator('h2:has-text("Paper Portfolio")');
    await expect(heading).toBeVisible();
  });

  test('Community Backtests section renders on load', async ({ page }) => {
    // BacktestHistory component is always visible
    const heading = page.locator('h2:has-text("Community Backtests")');
    await expect(heading).toBeVisible();
  });

  test('Paper Portfolio shows portfolio data or loading state', async ({ page }) => {
    // Use CSS :has() to find the card containing the Paper Portfolio heading
    const card = page.locator('.card:has(h2:has-text("Paper Portfolio"))');
    await expect(card).toBeVisible({ timeout: 10000 });
    // After card is visible, wait for either a currency value or the loading message
    const hasValue = card.locator('text=/\\$[0-9]/').first();
    const hasLoading = card.locator('text=/Simulating/i').first();
    await expect(hasValue.or(hasLoading)).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: 'artifacts/paper-portfolio.png' });
  });
});
