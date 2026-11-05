import { test, expect } from '@playwright/test';
import * as path from 'path';

const OUT = process.env.DOCS_IMG_DIR ?? path.resolve(__dirname, '../../docs/img');

test('capture live map and trip replay for the README', async ({ page, request }) => {
  await page.goto('/');
  await page.selectOption('#login-role', 'operator');
  await page.click('#btn-login');
  await expect(page.locator('#auth-state')).toContainText('[RIDER,ADMIN]');
  await page.click('#btn-sim');

  const tok = await request.post('/api/auth/token', { data: { subject: 'capture', roles: ['RIDER'] } });
  const rider = `Bearer ${(await tok.json()).accessToken}`;
  await expect.poll(async () => {
    const r = await request.get('/api/drivers/nearby?lat=37.7749&lon=-122.4194&radiusKm=10', { headers: { Authorization: rider } });
    return (await r.json()).length;
  }, { timeout: 60_000 }).toBeGreaterThan(0);

  await page.click('#btn-request');
  await expect(page.locator('#trip-status')).toHaveText('DRIVER_ASSIGNED');
  await expect.poll(async () => Number(await page.locator('#update-count').textContent()), { timeout: 30_000 })
    .toBeGreaterThanOrEqual(6);
  await page.waitForTimeout(2500); // tiles + a few more marker moves for the recording
  await page.screenshot({ path: path.join(OUT, 'live-map.png') });

  await page.selectOption('#replay-speed', '10');
  await page.click('#btn-replay');
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, 'trip-replay.png') });
  await expect(page.locator('#replay-state')).toHaveText('finished', { timeout: 60_000 });
  await page.waitForTimeout(800);

  await request.post('/api/simulator/stop', {
    headers: { Authorization: `Bearer ${(await (await request.post('/api/auth/token', { data: { subject: 'ops', roles: ['ADMIN'] } })).json()).accessToken}` },
  });
});
