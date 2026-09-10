package com.wallettransfer.reconciliation.service;

import com.wallettransfer.reconciliation.dto.*;
import com.wallettransfer.reconciliation.exception.ReconciliationCaseNotFoundException;
import com.wallettransfer.reconciliation.repository.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationQueryService {
    private final ReconciliationRunRepository runs;
    private final ReconciliationCaseRepository cases;

    public ReconciliationQueryService(ReconciliationRunRepository runs, ReconciliationCaseRepository cases) {
        this.runs = runs;
        this.cases = cases;
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRunResponse> runs(int page, int size) {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(ReconciliationRunResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationCaseResponse> cases(int page, int size) {
        return cases.findAllByOrderByLastDetectedAtDesc(
                        PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(ReconciliationCaseResponse::from);
    }

    @Transactional(readOnly = true)
    public ReconciliationCaseResponse caseById(UUID id) {
        return ReconciliationCaseResponse.from(
                cases.findById(id).orElseThrow(ReconciliationCaseNotFoundException::new));
    }
}
