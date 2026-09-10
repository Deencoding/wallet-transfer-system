import http from 'k6/http';
import { check } from 'k6';
import { baseUrl, email } from '../config.js';

export const options = { vus: 5, iterations: 25 };

export default function () {
  const response = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({
    email: email(1), password: 'definitely-wrong-password',
  }), { headers: { 'Content-Type': 'application/json' }, tags: { operation: 'throttled-login' } });
  check(response, { 'rejected or throttled': value => [401, 429].includes(value.status) });
}
