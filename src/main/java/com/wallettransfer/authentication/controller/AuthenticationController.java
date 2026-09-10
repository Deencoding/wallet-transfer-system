package com.wallettransfer.authentication.controller;

import com.wallettransfer.authentication.dto.LoginRequest;
import com.wallettransfer.authentication.dto.RefreshTokenRequest;
import com.wallettransfer.authentication.dto.RegisterRequest;
import com.wallettransfer.authentication.dto.TokenResponse;
import com.wallettransfer.authentication.service.AuthenticationService;
import com.wallettransfer.users.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationService authentication;

    public AuthenticationController(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a customer")
    ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileResponse response = authentication.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and issue a token pair")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authentication.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token")
    TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authentication.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh-token family")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RefreshTokenRequest request) {
        authentication.logout(UUID.fromString(jwt.getSubject()), request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
