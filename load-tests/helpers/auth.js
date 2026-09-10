import http from 'k6/http';
import { check } from 'k6';
import { baseUrl, email, password, users } from '../config.js';

export function loadIdentities() {
  const identities = [];
  for (let index = 1; index <= users; index += 1) {
    const login = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({ email: email(index), password }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { operation: 'login-setup' },
    });
    check(login, { 'fixture login succeeds': response => response.status === 200 });
    const accessToken = login.json('accessToken');
    const wallet = http.get(`${baseUrl}/api/v1/wallets/me`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      tags: { operation: 'wallet-setup' },
    });
    check(wallet, { 'fixture wallet loads': response => response.status === 200 });
    identities.push({ accessToken, walletId: wallet.json('id') });
  }
  return identities;
}
