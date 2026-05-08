#!/usr/bin/env node
/**
 * STOMP smoke test — Sprint 4 §2.7 verification.
 *
 * Verifies three things end-to-end against a running backend:
 *   1. CONNECT with valid JWT succeeds
 *   2. SUBSCRIBE to your own org's topic succeeds
 *   3. SUBSCRIBE to a DIFFERENT org's topic is REJECTED
 *
 * Usage:
 *   cd tss-grc-ui-v2
 *   node ../.tools/stomp_smoke_test.js
 *
 * Requires the frontend's npm deps installed (uses @stomp/stompjs + sockjs-client).
 * Backend must be running on http://localhost:8084.
 */

import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const BASE = 'http://localhost:8084';

async function login(username, password) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status}`);
  const body = await res.json();
  return body.accessToken;
}

function decodeJwt(token) {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(payload, 'base64').toString('utf-8'));
}

function connectStomp(token) {
  return new Promise((resolve, reject) => {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${BASE}/ws`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,                    // fail fast in tests
      debug: () => { /* silence */ },
      onConnect: () => resolve(client),
      onStompError: (frame) => reject(new Error(`STOMP error: ${frame.headers.message ?? frame.body}`)),
      onWebSocketError: (e) => reject(new Error(`WS error: ${e.message ?? e}`)),
    });
    client.activate();
  });
}

async function subscribeAndExpect(client, topic, expect) {
  return new Promise((resolve) => {
    let resolved = false;
    const finish = (outcome) => { if (!resolved) { resolved = true; resolve(outcome); } };

    let sub;
    try {
      sub = client.subscribe(topic, (_msg) => { /* ignore message body */ });
      // If we got here without throwing, server accepted
      setTimeout(() => {
        if (sub) try { sub.unsubscribe(); } catch (_) {}
        finish({ accepted: true });
      }, 200);
    } catch (e) {
      finish({ accepted: false, reason: e.message });
    }

    // STOMP errors come asynchronously via the onStompError callback bound at
    // connect time — re-bind here to capture rejection on this specific frame.
    const orig = client.onStompError;
    client.onStompError = (frame) => {
      finish({ accepted: false, reason: frame.headers.message ?? frame.body });
      if (orig) orig.call(client, frame);
    };

    // Fallback safety
    setTimeout(() => finish({ accepted: 'timeout' }), 1500);
  });
}

(async () => {
  console.log('=== STOMP smoke test (Sprint 4 §2.7) ===\n');

  console.log('[1] Logging in as admin…');
  const token = await login('admin', 'Admin@12345');
  const claims = decodeJwt(token);
  const myOrgId = claims.org;
  console.log(`    JWT issued for userId=${claims.sub} orgId=${myOrgId}`);

  console.log('\n[2] Connecting to /ws with valid JWT…');
  const client = await connectStomp(token);
  console.log('    CONNECT accepted ✓');

  console.log(`\n[3] Subscribing to own-org topic /topic/org.${myOrgId}.changed.vulnerability …`);
  const own = await subscribeAndExpect(client, `/topic/org.${myOrgId}.changed.vulnerability`);
  console.log(`    Result: ${own.accepted === true ? 'ACCEPTED ✓' : `REJECTED ✗ (${own.reason})`}`);

  const otherOrgId = Number(myOrgId) === 999 ? 1 : 999;
  console.log(`\n[4] Subscribing to OTHER-org topic /topic/org.${otherOrgId}.changed.vulnerability …`);
  console.log('    (this MUST be rejected)');
  const cross = await subscribeAndExpect(client, `/topic/org.${otherOrgId}.changed.vulnerability`);
  console.log(`    Result: ${cross.accepted === true
    ? 'ACCEPTED ✗ (BUG — cross-tenant subscription should be rejected!)'
    : `REJECTED ✓ (${cross.reason})`}`);

  console.log('\n[5] Cleanup');
  client.deactivate();

  console.log('\n=== Summary ===');
  const ok =
    own.accepted === true &&
    cross.accepted !== true;
  console.log(ok ? '✓ All checks passed.' : '✗ Some checks failed — see above.');
  process.exit(ok ? 0 : 1);
})().catch((e) => {
  console.error('FATAL:', e);
  process.exit(2);
});
