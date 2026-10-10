// Driver location firehose: many drivers POSTing locations.  k6 run loadtest/location-ingest.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE, CENTER, jitter, token, authHeaders } from './common.js';

export const options = {
  scenarios: {
    drivers: {
      executor: 'ramping-arrival-rate',
      startRate: 20, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 200,
      stages: [{ target: 200, duration: '30s' }, { target: 200, duration: '1m' }, { target: 0, duration: '10s' }],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

const DRIVERS = 100;
const tokens = {};

export default function () {
  const id = `load-driver-${Math.floor(Math.random() * DRIVERS)}`;
  if (!tokens[id]) tokens[id] = token(id, 'DRIVER');
  const res = http.post(`${BASE}/api/drivers/${id}/location`, JSON.stringify({
    lat: jitter(CENTER.lat), lon: jitter(CENTER.lon), heading: Math.random() * 360, speedKmh: 30 + Math.random() * 20,
  }), authHeaders(tokens[id]));
  check(res, { 'accepted': (r) => r.status === 202 });
}
