import { check, sleep } from 'k6';
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
    'http_req_duration{operation:internal-transfer}': ['p(95)<750', 'p(99)<1500'],
  },
};

export function setup() { return loadIdentities(); }

export default function (identities) {
  const senderIndex = (__VU - 1) % identities.length;
  const receiverIndex = (senderIndex + 1) % identities.length;
  const response = transfer(identities[senderIndex], identities[receiverIndex].walletId, '1.00',
    `steady-${__VU}-${__ITER}-${Date.now()}`, 'steady load transfer');
  check(response, { 'transfer succeeds': value => value.status === 201 });
  sleep(0.05);
}
