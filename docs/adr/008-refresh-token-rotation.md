# ADR-008: Refresh-token rotation

**Status:** Accepted

Persist only refresh-token fingerprints and rotate each token under a PostgreSQL row lock. Reuse revokes the active family. Access tokens remain stateless and expire after 15 minutes, so logout is not immediate access-token revocation; this avoids a shared lookup on every API call.
