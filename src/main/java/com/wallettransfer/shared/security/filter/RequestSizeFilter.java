package com.wallettransfer.shared.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallettransfer.shared.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestSizeFilter extends OncePerRequestFilter {

    static final long MAX_API_BODY_BYTES = 64 * 1024;
    private final ObjectMapper mapper;

    public RequestSizeFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_API_BODY_BYTES) {
            response.setStatus(413);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(
                    response.getOutputStream(),
                    new ApiError("REQUEST_TOO_LARGE", "Request body exceeds the permitted size"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
