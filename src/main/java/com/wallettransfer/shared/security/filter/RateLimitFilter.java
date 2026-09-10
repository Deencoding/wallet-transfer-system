package com.wallettransfer.shared.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.shared.error.ApiError;
import com.wallettransfer.shared.security.model.RateLimitPolicy;
import com.wallettransfer.shared.security.service.RateLimitService;
import com.wallettransfer.shared.security.service.SecurityAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService limits;
    private final SecurityAuditService audit;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimitService limits, SecurityAuditService audit, ObjectMapper mapper) {
        this.limits = limits;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitPolicy policy = policy(request);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }
        String principal = principal(request);
        String key = policy.name() + ":" + audit.fingerprint(principal);
        var decision = limits.consume(key, policy);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        audit.rejected("RATE_LIMIT", policy.name(), request.getRemoteAddr(), "LIMIT_EXCEEDED");
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(
                response.getOutputStream(), new ApiError("RATE_LIMIT_EXCEEDED", "Too many requests. Try again later."));
    }

    private String principal(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return request.getRemoteAddr();
    }

    private RateLimitPolicy policy(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (path.equals("/api/v1/auth/register")) {
            return new RateLimitPolicy("register", 5, Duration.ofHours(1));
        }
        if (path.equals("/api/v1/auth/login")) {
            return new RateLimitPolicy("login", 10, Duration.ofMinutes(5));
        }
        if (path.equals("/api/v1/auth/refresh")) {
            return new RateLimitPolicy("refresh", 20, Duration.ofMinutes(1));
        }
        if (path.matches("/api/v1/transfers/[^/]+/reverse"))
            return new RateLimitPolicy("reversal", 10, Duration.ofMinutes(1));
        if (path.equals("/api/v1/transfers")) {
            return new RateLimitPolicy("transfer", 30, Duration.ofMinutes(1));
        }
        if (path.equals("/api/v1/external-transfers"))
            return new RateLimitPolicy("external-transfer", 10, Duration.ofMinutes(1));
        if (path.equals("/api/v1/admin/reconciliation/runs"))
            return new RateLimitPolicy("reconciliation", 3, Duration.ofHours(1));
        if (path.startsWith("/api/v1/webhooks/providers/"))
            return new RateLimitPolicy("webhook", 120, Duration.ofMinutes(1));
        return null;
    }
}
