# ADR-007: External provider uncertainty

**Status:** Accepted

Reserve funds before calling a provider, use a stable provider request reference, and treat observed timeouts as uncertain rather than failed. Resolve uncertainty through idempotent status query, signed webhook, and reconciliation. This avoids a blind retry that could duplicate a provider-side debit, at the cost of temporarily reserved funds.
