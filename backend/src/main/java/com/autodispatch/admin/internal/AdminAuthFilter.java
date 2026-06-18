package com.autodispatch.admin.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Guards all /api/admin/** endpoints with a static API key.
 * Constant-time comparison via MessageDigest.isEqual prevents timing attacks.
 */
@Component
class AdminAuthFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Admin-Key";

    private final byte[] expectedKeyBytes;

    AdminAuthFilter(AdminProperties properties) {
        this.expectedKeyBytes = properties.apiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/admin/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null && MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), expectedKeyBytes)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,\
                "detail":"A valid X-Admin-Key header is required."}""");
    }
}
