export const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
export const password = __ENV.LOAD_PASSWORD || 'load-test-password';
export const users = 20;

export function email(index) {
  return `load-user-${String(index).padStart(3, '0')}@example.test`;
}
