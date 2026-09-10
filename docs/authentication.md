# Authentication and user security

Access and refresh tokens are RSA-signed JWTs with separate audiences and explicit `token_type` claims. Access tokens live for 15 minutes and are verified statelessly. Refresh tokens live for 30 days, but their SHA-256 fingerprints, JWT IDs, family IDs, status, and rotation history are persisted in PostgreSQL.

Every refresh locks its token row, revokes the presented token, and creates one successor in the same transaction. Reuse of a rotated token revokes the active family. Concurrent refreshes therefore cannot create two independently valid chains. Logout revokes the family; already-issued access tokens remain usable for at most their short remaining lifetime.

Production requires external RSA keys. `JWT_PUBLIC_KEY` accepts an X.509 public key and `JWT_PRIVATE_KEY` accepts a PKCS#8 private key, as PEM text, escaped PEM text, or base64 DER. Development generates an ephemeral 2048-bit pair when neither value is configured, so local tokens do not survive an application restart.

Passwords use BCrypt cost 12. Inputs must contain at least 12 characters and no more than 72 UTF-8 bytes; exceeding BCrypt's byte limit is rejected instead of truncated. Email identities are trimmed and lowercased, with uniqueness enforced in PostgreSQL.
