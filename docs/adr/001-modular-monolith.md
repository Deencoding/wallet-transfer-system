# ADR-001: Modular monolith

**Status:** Accepted

Use one deployable with domain-oriented module boundaries. Internal transfers need one reliable database transaction, and a modular monolith avoids distributed transaction and operational complexity. Architecture tests guard coupling so independently scaling modules may later be extracted. The limitation is a shared deployment and database failure boundary.
