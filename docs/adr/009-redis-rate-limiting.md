# ADR-009: Redis rate limiting outside financial correctness

**Status:** Accepted

Use an atomic Redis Lua counter with HMAC-pseudonymized keys for sensitive endpoint throttling. Production fails closed when Redis is unavailable. Redis is deliberately excluded from wallet locking and balance correctness; losing Redis can reduce availability but cannot create money or authorize a stale balance.
