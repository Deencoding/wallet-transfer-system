# ADR-003: Monetary representation

**Status:** Accepted

Use Java `BigDecimal` and PostgreSQL `NUMERIC(19,2)` for the initial NGN-only product. Inputs with more than two fractional digits are rejected; no implicit rounding occurs. Currency remains explicit in records. Supporting currencies with different scales later requires extending the currency policy and reviewing column scale.
