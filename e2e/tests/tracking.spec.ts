import { test, expect } from '@playwright/test';

test.describe('live tracking UI', () => {
  test.afterEach(async ({ request }) => {
    await request.post('/api/simulator/stop');
  });

  test('page loads with map and controls', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#map .leaflet-container, #map.leaflet-container')).toHaveCount(1);
    await expect(page.locator('#btn-sim')).toBeVisible();
    await expect(page.locator('#conn')).toHaveText('idle');
  });

  test('request a ride and watch the driver move in real time', async ({ page, request }) => {
    await page.goto('/');
    await page.click('#btn-sim');

    // wait until simulated drivers have flowed Kafka -> Redis GEO and are discoverable
    await expect.poll(async () => {
      const r = await request.get('/api/drivers/nearby?lat=37.7749&lon=-122.4194&radiusKm=10');
      return (await r.json()).length;
    }, { timeout: 45_000 }).toBeGreaterThan(0);

    await page.click('#btn-request');
    await expect(page.locator('#trip-id')).not.toHaveValue('');
    await expect(page.locator('#trip-status')).toHaveText('DRIVER_ASSIGNED');
    await expect(page.locator('#conn')).toHaveText('connected');

    // live location frames arrive over the WebSocket and the marker moves
    await expect(page.locator('#driver-id')).toContainText('sim-driver-');
    await expect.poll(async () => Number(await page.locator('#update-count').textContent()), { timeout: 30_000 })
      .toBeGreaterThanOrEqual(3);
    await expect(page.locator('.leaflet-marker-icon')).toHaveCount(1);
    const pos1 = await page.locator('#position').textContent();
    await expect.poll(async () => page.locator('#position').textContent()).not.toBe(pos1);

    // status transitions through the UI
    await page.click('#btn-arrived');
    await expect(page.locator('#trip-status')).toHaveText('DRIVER_ARRIVED');
    await page.click('#btn-start');
    await expect(page.locator('#trip-status')).toHaveText('IN_PROGRESS');
    await page.click('#btn-complete');
    await expect(page.locator('#trip-status')).toHaveText('COMPLETED');
  });

  test('REST API rejects illegal transitions and unknown trips', async ({ request }) => {
    const missing = await request.get('/api/trips/00000000-0000-0000-0000-000000000000');
    expect(missing.status()).toBe(404);
    const trip = await request.post('/api/rides', {
      data: { riderId: 'e2e', pickupLat: -33.0, pickupLon: 151.0, dropoffLat: -33.1, dropoffLon: 151.1 },
    });
    expect(trip.status()).toBe(201);
    const body = await trip.json();
    expect(body.status).toBe('NO_DRIVER_AVAILABLE');
    const bad = await request.patch(`/api/trips/${body.id}/status`, { data: { status: 'COMPLETED' } });
    expect(bad.status()).toBe(409);
  });
});
