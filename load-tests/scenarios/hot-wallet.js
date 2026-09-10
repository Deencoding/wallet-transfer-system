import { check } from 'k6';
import { loadIdentities } from '../helpers/auth.js';
import { transfer } from '../helpers/transfers.js';

export const options = {
  scenarios: { contention: { executor: 'shared-iterations', vus: 50, iterations: 200, maxDuration: '1m' } },
};

export function setup() { return loadIdentities(); }

export default function (identities) {
  const response = transfer(identities[0], identities[1].walletId, '10.00',
    `hot-${__VU}-${__ITER}-${Date.now()}`, 'hot wallet contention');
  check(response, { 'financially valid response': value => [201, 409].includes(value.status) });
}
