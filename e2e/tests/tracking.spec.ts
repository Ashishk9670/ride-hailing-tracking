import { test, expect, APIRequestContext } from '@playwright/test';

async function tokenFor(request: APIRequestContext, subject: string, roles: string[]) {
  const r = await request.post('/api/auth/token', { data: { subject, roles } });
  expect(r.status()).toBe(201);
  return `Bearer ${(await r.json()).accessToken}`;
}

test.describe('live tracking UI', () => {
  test.afterEach(async ({ request }) => {
    const auth = await tokenFor(request, 'ops', ['ADMIN']);
    await request.post('/api/simulator/stop', { headers: { Authorization: auth } });
  });

  test('page loads with map and controls', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#map .leaflet-container, #map.leaflet-container')).toHaveCount(1);
    await expect(page.locator('#btn-sim')).toBeVisible();
    await expect(page.locator('#conn')).toHaveText('idle');
    await expect(page.locator('#auth-state')).toHaveText('signed out');
  });

  test('anonymous UI actions are refused until signed in', async ({ page }) => {
    await page.goto('/');
    await page.click('#btn-sim');
    await expect(page.locator('#log')).toContainText('-> 401');
  });

  test('a plain rider cannot control the simulator (403)', async ({ page }) => {
    await page.goto('/');
    await page.selectOption('#login-role', 'rider');
    await page.click('#btn-login');
    await expect(page.locator('#auth-state')).toContainText('signed in as ui-rider [RIDER]');
    await page.click('#btn-sim');
    await expect(page.locator('#log')).toContainText('-> 403');
  });

  test('request a ride and watch the driver move in real time', async ({ page, request }) => {
    await page.goto('/');
    await page.selectOption('#login-role', 'operator');
    await page.click('#btn-login');
    await expect(page.locator('#auth-state')).toContainText('[RIDER,ADMIN]');
    await page.click('#btn-sim');

    // wait until simulated drivers have flowed Kafka -> Redis GEO and are discoverable
    const rider = await tokenFor(request, 'poller', ['RIDER']);
    await expect.poll(async () => {
      const r = await request.get('/api/drivers/nearby?lat=37.7749&lon=-122.4194&radiusKm=10', { headers: { Authorization: rider } });
      return (await r.json()).length;
    }, { timeout: 45_000 }).toBeGreaterThan(0);

    await page.click('#btn-request');
    await expect(page.locator('#trip-id')).not.toHaveValue('');
    await expect(page.locator('#trip-status')).toHaveText('DRIVER_ASSIGNED');
    await expect(page.locator('#conn')).toHaveText('connected');

    // live location frames arrive over the authenticated WebSocket and the marker moves
    await expect(page.locator('#driver-id')).toContainText('sim-driver-');
    await expect.poll(async () => Number(await page.locator('#update-count').textContent()), { timeout: 30_000 })
      .toBeGreaterThanOrEqual(3);
    await expect(page.locator('.leaflet-marker-icon')).toHaveCount(1);
    const pos1 = await page.locator('#position').textContent();
    await expect.poll(async () => page.locator('#position').textContent()).not.toBe(pos1);
    await page.waitForTimeout(1500); // let map tiles paint for the screenshot
    await page.screenshot({ path: 'screenshots/live-tracking.png' });

    // status transitions through the UI
    await page.click('#btn-arrived');
    await expect(page.locator('#trip-status')).toHaveText('DRIVER_ARRIVED');
    await page.click('#btn-start');
    await expect(page.locator('#trip-status')).toHaveText('IN_PROGRESS');
    await page.click('#btn-complete');
    await expect(page.locator('#trip-status')).toHaveText('COMPLETED');
    await page.screenshot({ path: 'screenshots/trip-completed.png' });
  });

  test('raw WebSocket refuses connections without a valid token', async ({ page }) => {
    await page.goto('/');
    const result = await page.evaluate(() => new Promise<string>((resolve) => {
      const ws = new WebSocket(`ws://${location.host}/ws/trips/00000000-0000-0000-0000-000000000000`);
      ws.onopen = () => resolve('open');
      ws.onerror = () => resolve('error');
    }));
    expect(result).toBe('error');
  });

  test('REST API enforces auth, ownership, rejects illegal transitions and unknown trips', async ({ request }) => {
    expect((await request.post('/api/rides', {
      data: { riderId: 'e2e', pickupLat: 1, pickupLon: 1, dropoffLat: 1, dropoffLon: 1 } })).status()).toBe(401);
    const rider = { Authorization: await tokenFor(request, 'e2e', ['RIDER']) };
    const other = { Authorization: await tokenFor(request, 'someone-else', ['RIDER']) };

    expect((await request.get('/api/trips/00000000-0000-0000-0000-000000000000', { headers: rider })).status()).toBe(404);
    // riderId must match the authenticated subject
    expect((await request.post('/api/rides', { headers: other,
      data: { riderId: 'e2e', pickupLat: -33.0, pickupLon: 151.0, dropoffLat: -33.1, dropoffLon: 151.1 } })).status()).toBe(403);
    const trip = await request.post('/api/rides', { headers: rider,
      data: { riderId: 'e2e', pickupLat: -33.0, pickupLon: 151.0, dropoffLat: -33.1, dropoffLon: 151.1 } });
    expect(trip.status()).toBe(201);
    const body = await trip.json();
    expect(body.status).toBe('NO_DRIVER_AVAILABLE');
    const bad = await request.patch(`/api/trips/${body.id}/status`, { headers: rider, data: { status: 'COMPLETED' } });
    expect(bad.status()).toBe(409);

    // a rider token cannot publish driver locations
    expect((await request.post('/api/drivers/d1/location', { headers: rider,
      data: { lat: 1, lon: 1, heading: 0, speedKmh: 0 } })).status()).toBe(403);
    const driver = { Authorization: await tokenFor(request, 'd1', ['DRIVER']) };
    expect((await request.post('/api/drivers/d1/location', { headers: driver,
      data: { lat: 1, lon: 1, heading: 0, speedKmh: 0 } })).status()).toBe(202);
  });

  test('Prometheus metrics are exposed', async ({ request }) => {
    const r = await request.get('/actuator/prometheus');
    expect(r.status()).toBe(200);
    const text = await r.text();
    expect(text).toContain('rides_matches_total');
    expect(text).toContain('driver_location_updates_total');
  });
});
