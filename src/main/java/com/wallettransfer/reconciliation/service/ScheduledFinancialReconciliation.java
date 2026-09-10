package com.wallettransfer.reconciliation.service;

import com.wallettransfer.reconciliation.exception.ReconciliationAlreadyRunningException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "application.financial-reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ScheduledFinancialReconciliation {
    private final FinancialReconciliationService service;

    public ScheduledFinancialReconciliation(FinancialReconciliationService service) {
        this.service = service;
    }

    @Scheduled(cron = "${application.financial-reconciliation.cron:0 0 2 * * *}")
    public void run() {
        try {
            service.run(null);
        } catch (ReconciliationAlreadyRunningException ignored) {
        }
    }
}
