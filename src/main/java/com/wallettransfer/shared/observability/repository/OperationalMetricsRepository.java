package com.wallettransfer.shared.observability.repository;

import com.wallettransfer.shared.observability.model.OperationalBacklogSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationalMetricsRepository {

    private static final String SQL = "SELECT "
            + "count(*) FILTER (WHERE status IN ('PENDING','FAILED')) pending_outbox, "
            + "count(*) FILTER (WHERE status = 'PROCESSING') processing_outbox, "
            + "count(*) FILTER (WHERE status = 'DEAD') dead_outbox, "
            + "COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - min(created_at) "
            + "FILTER (WHERE status IN ('PENDING','FAILED')))), 0) oldest_age, "
            + "(SELECT count(*) FROM reconciliation_cases WHERE status IN ('OPEN','ACKNOWLEDGED')) open_cases, "
            + "(SELECT count(*) FROM reconciliation_cases WHERE status IN ('OPEN','ACKNOWLEDGED') "
            + "AND severity = 'CRITICAL') critical_cases FROM outbox_events";

    private final JdbcTemplate jdbc;

    public OperationalMetricsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OperationalBacklogSnapshot snapshot() {
        return jdbc.queryForObject(
                SQL,
                (row, number) -> new OperationalBacklogSnapshot(
                        row.getLong("pending_outbox"),
                        row.getLong("processing_outbox"),
                        row.getLong("dead_outbox"),
                        row.getDouble("oldest_age"),
                        row.getLong("open_cases"),
                        row.getLong("critical_cases")));
    }
}
