package com.wallettransfer.reconciliation.controller;

import com.wallettransfer.reconciliation.dto.*;
import com.wallettransfer.reconciliation.service.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationController {
    private final FinancialReconciliationService financial;
    private final ReconciliationQueryService queries;
    private final ReconciliationRepairService repairs;

    public ReconciliationController(
            FinancialReconciliationService financial,
            ReconciliationQueryService queries,
            ReconciliationRepairService repairs) {
        this.financial = financial;
        this.queries = queries;
        this.repairs = repairs;
    }

    @PostMapping("/runs")
    public ResponseEntity<ReconciliationRunResponse> run(@AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        ReconciliationRunResponse response = financial.run(actorId);
        URI location = URI.create("/api/v1/admin/reconciliation/runs/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/runs")
    public Page<ReconciliationRunResponse> runs(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return queries.runs(page, size);
    }

    @GetMapping("/cases")
    public Page<ReconciliationCaseResponse> cases(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return queries.cases(page, size);
    }

    @GetMapping("/cases/{id}")
    public ReconciliationCaseResponse caseById(@PathVariable UUID id) {
        return queries.caseById(id);
    }

    @PostMapping("/cases/{id}/repair-wallet-projection")
    public ResponseEntity<ReconciliationRepairResponse> repair(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RepairWalletProjectionRequest request) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        ReconciliationRepairResponse response = repairs.repairWallet(actorId, id, key, request);
        URI location = URI.create("/api/v1/admin/reconciliation/cases/" + response.caseId());
        return ResponseEntity.created(location).body(response);
    }
}
