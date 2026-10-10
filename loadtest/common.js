import http from 'k6/http';
import { check } from 'k6';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HDR = { 'Content-Type': 'application/json' };

// Uses the dev token endpoint (DEV_TOKEN_ENDPOINT_ENABLED=true).
export function token(subject, role) {
  const res = http.post(`${BASE}/api/auth/token`,
    JSON.stringify({ subject, roles: [role] }), { headers: JSON_HDR });
  check(res, { 'token issued': (r) => r.status === 201 });
  return res.json('accessToken');
}

export function authHeaders(t) {
  return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${t}` } };
}

// Roughly central Bengaluru
export function jitter(base, spread = 0.02) { return base + (Math.random() - 0.5) * spread; }
export const CENTER = { lat: 12.9716, lon: 77.5946 };
