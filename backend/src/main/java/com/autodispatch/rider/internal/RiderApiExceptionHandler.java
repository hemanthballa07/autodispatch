package com.autodispatch.rider.internal;

import com.autodispatch.dispatch.api.ActiveRideExistsException;
import com.autodispatch.dispatch.api.RideNotCancellableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC-7807 problem JSON for the rider API.
 */
@RestControllerAdvice(basePackages = "com.autodispatch.rider")
class RiderApiExceptionHandler {

    @ExceptionHandler(ApiExceptions.NotFoundException.class)
    ProblemDetail notFound(ApiExceptions.NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(ApiExceptions.UnprocessableException.class)
    ProblemDetail unprocessable(ApiExceptions.UnprocessableException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable request", e.getMessage());
    }

    @ExceptionHandler(ApiExceptions.RateLimitedException.class)
    ProblemDetail rateLimited(ApiExceptions.RateLimitedException e) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many ride requests", e.getMessage());
    }

    @ExceptionHandler(ActiveRideExistsException.class)
    ProblemDetail activeRide(ActiveRideExistsException e) {
        return problem(HttpStatus.CONFLICT, "Active ride exists",
                "You already have an active ride. Finish or cancel it first.");
    }

    @ExceptionHandler(RideNotCancellableException.class)
    ProblemDetail notCancellable(RideNotCancellableException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Ride not cancellable",
                "This ride can no longer be cancelled (state: %s).".formatted(e.currentStatus()));
        problem.setProperty("currentStatus", e.currentStatus());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                e.getBindingResult().getFieldErrors().stream()
                        .map(err -> err.getField() + ": " + err.getDefaultMessage())
                        .findFirst()
                        .orElse("Invalid request body"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
