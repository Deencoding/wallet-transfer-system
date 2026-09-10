import http from 'k6/http';
import { baseUrl } from '../config.js';

export function transfer(sender, receiverWalletId, amount, idempotencyKey, description) {
  return http.post(`${baseUrl}/api/v1/transfers`, JSON.stringify({
    receiverWalletId,
    amount,
    currency: 'NGN',
    description,
  }), {
    headers: {
      Authorization: `Bearer ${sender.accessToken}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    tags: { operation: 'internal-transfer' },
  });
}
