package com.autodispatch.rider.internal;

/**
 * Rider-API-local exceptions, mapped to RFC-7807 responses by
 * {@link RiderApiExceptionHandler}.
 */
final class ApiExceptions {

    private ApiExceptions() {
    }

    /** 404 — also used for other riders' rides so existence never leaks. */
    static class NotFoundException extends RuntimeException {
        NotFoundException(String message) {
            super(message);
        }
    }

    /** 422 — semantically invalid request (unknown location, no fare rule). */
    static class UnprocessableException extends RuntimeException {
        UnprocessableException(String message) {
            super(message);
        }
    }

    /** 429 — ride-creation rate limit hit. */
    static class RateLimitedException extends RuntimeException {
        RateLimitedException(String message) {
            super(message);
        }
    }
}
