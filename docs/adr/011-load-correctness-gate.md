# ADR-011: Load testing with financial correctness gates

**Status:** Accepted

Use external k6 workloads to characterize latency and contention, then fail the run if SQL financial invariants do not hold. Benchmarks run in an isolated seeded environment and report hardware/configuration. Throughput never substitutes for ledger balance, conservation, idempotency, or projection checks.
