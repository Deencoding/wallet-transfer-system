import { check } from 'k6';
import { loadIdentities } from '../helpers/auth.js';
import { transfer } from '../helpers/transfers.js';

export const options = {
  scenarios: { storm: { executor: 'shared-iterations', vus: 50, iterations: 50, maxDuration: '30s' } },
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export function setup() { return loadIdentities(); }

export default function (identities) {
  const response = transfer(identities[0], identities[1].walletId, '10.00',
    'idempotency-storm-shared-key', 'idempotency storm transfer');
  check(response, {
    'replay is successful': value => value.status === 201,
    'reference is returned': value => Boolean(value.json('reference')),
  });
}
