// Ride requests against a pool of live drivers.  k6 run loadtest/ride-matching.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE, CENTER, jitter, token, authHeaders } from './common.js';

export const options = {
  scenarios: {
    drivers: { executor: 'constant-vus', vus: 20, duration: '1m', exec: 'driver' },
    riders: { executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '1m',
              preAllocatedVUs: 20, maxVUs: 100, exec: 'rider' },
  },
  thresholds: {
    'http_req_duration{name:request_ride}': ['p(95)<500'],
    checks: ['rate>0.99'],
  },
};

export function driver() {
  const id = `load-driver-${__VU}`;
  const t = token(id, 'DRIVER');
  http.post(`${BASE}/api/drivers/${id}/location`, JSON.stringify({
    lat: jitter(CENTER.lat, 0.01), lon: jitter(CENTER.lon, 0.01), heading: 0, speedKmh: 0,
  }), authHeaders(t));
  sleep(1);
}

export function rider() {
  const id = `load-rider-${__VU}`;
  const t = token(id, 'RIDER');
  const res = http.post(`${BASE}/api/rides`, JSON.stringify({
    riderId: id,
    pickupLat: jitter(CENTER.lat, 0.01), pickupLon: jitter(CENTER.lon, 0.01),
    dropoffLat: jitter(CENTER.lat, 0.05), dropoffLon: jitter(CENTER.lon, 0.05),
  }), { ...authHeaders(t), tags: { name: 'request_ride' } });
  // 201 for both matched and NO_DRIVER_AVAILABLE outcomes
  check(res, { 'ride created': (r) => r.status === 201 });
}
