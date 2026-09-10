import { check, sleep } from 'k6';
import { loadIdentities } from '../helpers/auth.js';
import { transfer } from '../helpers/transfers.js';

export const options = {
  vus: 1,
  iterations: 5,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{operation:internal-transfer}': ['p(95)<750'],
  },
};

export function setup() { return loadIdentities(); }

export default function (identities) {
  const response = transfer(identities[0], identities[1].walletId, '1.00',
    `smoke-${__VU}-${__ITER}-${Date.now()}`, 'load smoke transfer');
  check(response, {
    'transfer created': value => value.status === 201,
    'transfer successful': value => value.json('status') === 'SUCCESSFUL',
  });
  sleep(0.1);
}
