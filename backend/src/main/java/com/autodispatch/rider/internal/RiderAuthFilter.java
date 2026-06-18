package com.autodispatch.rider.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Bearer-token guard for the rider API. Everything under /api/v1/ except
 * session creation requires a valid JWT; the rider id is exposed to
 * controllers as the "riderId" request attribute.
 */
@Component
class RiderAuthFilter extends OncePerRequestFilter {

    static final String RIDER_ID_ATTRIBUTE = "riderId";

    private final JwtCodec jwtCodec;

    RiderAuthFilter(JwtCodec jwtCodec) {
        this.jwtCodec = jwtCodec;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/")
                || path.equals("/api/v1/sessions")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<UUID> riderId = jwtCodec.verify(header.substring("Bearer ".length()).trim());
            if (riderId.isPresent()) {
                request.setAttribute(RIDER_ID_ATTRIBUTE, riderId.get());
                chain.doFilter(request, response);
                return;
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401,\
                "detail":"A valid Bearer session token is required."}""");
    }
}
