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
        int pageNumber = Math.max(0, page);
        int minimumSize = Math.max(1, size);
        int pageSize = Math.min(100, minimumSize);
        var pageable = PageRequest.of(pageNumber, pageSize);
        var reconciliationRuns = runs.findAllByOrderByStartedAtDesc(pageable);
        return reconciliationRuns.map(ReconciliationRunResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationCaseResponse> cases(int page, int size) {
        int pageNumber = Math.max(0, page);
        int minimumSize = Math.max(1, size);
        int pageSize = Math.min(100, minimumSize);
        var pageable = PageRequest.of(pageNumber, pageSize);
        var reconciliationCases = cases.findAllByOrderByLastDetectedAtDesc(pageable);
        return reconciliationCases.map(ReconciliationCaseResponse::from);
    }

    @Transactional(readOnly = true)
    public ReconciliationCaseResponse caseById(UUID id) {
        var reconciliationCase = cases.findById(id).orElseThrow(ReconciliationCaseNotFoundException::new);
        return ReconciliationCaseResponse.from(reconciliationCase);
    }
}
