package com.wallettransfer.providers.controller;

import com.wallettransfer.providers.service.ProviderWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/providers")
public class ProviderWebhookController {
    private final ProviderWebhookService service;

    public ProviderWebhookController(ProviderWebhookService service) {
        this.service = service;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Void> receive(
            @PathVariable String provider,
            @RequestHeader("X-Provider-Event-ID") String eventId,
            @RequestHeader("X-Provider-Timestamp") String timestamp,
            @RequestHeader("X-Provider-Signature") String signature,
            @RequestBody String body) {
        service.process(provider, eventId, timestamp, signature, body);
        return ResponseEntity.ok().build();
    }
}
