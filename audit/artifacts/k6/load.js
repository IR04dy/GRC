import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = 'http://localhost:8084';
const authRead = new Trend('auth_read_latency', true);
const healthLat = new Trend('health_latency', true);
const errors = new Rate('errors');

export const options = {
  scenarios: {
    baseline:   { executor: 'ramping-vus', startVUs: 0, stages: [{ duration: '10s', target: 20 }, { duration: '20s', target: 20 }, { duration: '5s', target: 0 }], gracefulStop: '5s', exec: 'readRisks' },
    spike:      { executor: 'ramping-vus', startTime: '40s', startVUs: 0, stages: [{ duration: '5s', target: 5 }, { duration: '10s', target: 100 }, { duration: '10s', target: 5 }, { duration: '5s', target: 0 }], gracefulStop: '5s', exec: 'readRisks' },
    soak:       { executor: 'constant-vus', startTime: '75s', vus: 15, duration: '45s', exec: 'readRisks' },
    breakpoint: { executor: 'ramping-arrival-rate', startTime: '125s', startRate: 10, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 350, stages: [{ duration: '45s', target: 350 }], exec: 'readRisks' },
  },
  thresholds: {
    'http_req_duration{scenario:baseline}': ['p(95)<1500'],
    'errors': ['rate<0.10'],
  },
};

export function setup() {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ username: 'admin', password: 'Admin@12345' }), { headers: { 'Content-Type': 'application/json' } });
  return { token: res.json('accessToken') };
}

export function readRisks(data) {
  const res = http.get(`${BASE}/api/v1/risk/risks`, { headers: { Authorization: `Bearer ${data.token}` } });
  const ok = check(res, { 'status 200': r => r.status === 200 });
  errors.add(!ok);
  authRead.add(res.timings.duration);
  sleep(0.2);
}

export function handleSummary(data) {
  return { 'D:\\AYMAN\\GRC\\audit\\raw\\k6-summary.json': JSON.stringify(data, null, 2) };
}
