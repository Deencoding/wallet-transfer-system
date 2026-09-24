# Security hardening

## Authentication and tokens

Passwords use BCrypt cost 12 and are rejected if they are shorter than 12 characters, exceed BCrypt's 72-byte UTF-8 limit, are blank, or contain control characters. Authentication uses a fixed dummy hash for unknown users to reduce timing-based account discovery.

JWTs are RSA/RS256 signed. Decoders validate issuer, audience, token purpose, timestamps, UUID subject and token ID, and required claims. Configured RSA keys must be at least 2048 bits and the public/private pair is verified at startup. Production does not generate fallback keys.

Refresh tokens rotate under a PostgreSQL row lock. Reuse revokes the active family. Logout revokes refresh tokens; an existing access token can remain valid for at most its 15-minute lifetime. Immediate access-token revocation is deliberately not claimed.

## Rate limiting

Sensitive POST endpoints use an atomic Redis fixed-window counter implemented with Lua. Keys contain an HMAC fingerprint rather than a raw user ID, email, token, or client address. The socket peer address is used; arbitrary forwarded headers are not trusted by application code.

Production defaults to fail closed when Redis is unavailable. This preserves abuse controls at the cost of temporary endpoint availability. Redis is not used for wallet locking or financial correctness.

Rate limiting is enabled with `RATE_LIMITING_ENABLED=true`; production forces it on. `RATE_LIMIT_FAIL_CLOSED` controls the local/non-production failure policy.

## HTTP and API controls

The API is stateless, uses bearer authentication, and does not use authentication cookies, so CSRF is disabled. CORS is disabled by default. Responses include content-type, framing, referrer, permissions, CSP, and HTTPS HSTS protections. JSON with unknown fields is rejected, headers are capped at 16 KB, and declared request bodies above 64 KB receive HTTP 413.

Swagger and OpenAPI are disabled by default in production. Actuator remains on the isolated management port described in the observability guide.

## Security events and privacy

`security_events` is append-only at the database level. It records authentication outcomes, refresh-token reuse, logout, and throttling. Principals and client addresses are HMAC-pseudonymized with `SECURITY_AUDIT_PEPPER`. Raw passwords, emails, IP addresses, JWTs, refresh tokens, signatures, request bodies, and idempotency keys are not stored.

Production requires external values for JWT keys, database credentials, and the audit pepper. Placeholder values cause startup failure.

## Residual risks

- Declared body-size enforcement relies on `Content-Length`; the reverse proxy must also enforce request limits for chunked traffic.
- Rate limiting reduces abuse but does not replace edge DDoS protection or a web application firewall.
- Security events require an environment-specific retention and access-control policy.
