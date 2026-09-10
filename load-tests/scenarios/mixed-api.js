import http from 'k6/http';
import { check, sleep } from 'k6';
import { baseUrl } from '../config.js';
import { loadIdentities } from '../helpers/auth.js';
import { transfer } from '../helpers/transfers.js';

export const options = {
  stages: [
    { duration: __ENV.WARMUP || '30s', target: Number(__ENV.VUS || 25) },
    { duration: __ENV.DURATION || '5m', target: Number(__ENV.VUS || 25) },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<750', 'p(99)<1500'],
  },
};

export function setup() { return loadIdentities(); }

export default function (identities) {
  const senderIndex = (__VU - 1) % identities.length;
  const sender = identities[senderIndex];
  const selector = (__ITER + __VU) % 10;

  if (selector < 6) {
    const receiver = identities[(senderIndex + 1) % identities.length];
    const response = transfer(sender, receiver.walletId, '1.00',
      `mixed-${__VU}-${__ITER}-${Date.now()}`, 'mixed workload transfer');
    check(response, { 'mixed transfer succeeds': value => value.status === 201 });
  } else if (selector < 8) {
    const response = http.get(`${baseUrl}/api/v1/wallets/me`, {
      headers: { Authorization: `Bearer ${sender.accessToken}` },
      tags: { operation: 'wallet-read' },
    });
    check(response, { 'mixed wallet read succeeds': value => value.status === 200 });
  } else {
    const response = http.get(`${baseUrl}/api/v1/transfers?page=0&size=20`, {
      headers: { Authorization: `Bearer ${sender.accessToken}` },
      tags: { operation: 'transfer-list' },
    });
    check(response, { 'mixed transfer list succeeds': value => value.status === 200 });
  }

  sleep(0.05);
}
