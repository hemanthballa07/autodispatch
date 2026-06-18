package com.autodispatch.admin.internal;

import com.autodispatch.driver.api.DriverAlreadyExistsException;
import com.autodispatch.driver.api.DriverOnRideException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.autodispatch.admin")
class AdminApiExceptionHandler {

    @ExceptionHandler(DriverAlreadyExistsException.class)
    ProblemDetail conflict(DriverAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, "Driver already exists", e.getMessage());
    }

    @ExceptionHandler(DriverOnRideException.class)
    ProblemDetail onRide(DriverOnRideException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Driver is on a ride", e.getMessage());
    }

    @ExceptionHandler(AdminNotFoundException.class)
    ProblemDetail notFound(AdminNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed",
                e.getBindingResult().getFieldErrors().stream()
                        .map(err -> err.getField() + ": " + err.getDefaultMessage())
                        .findFirst()
                        .orElse("Invalid request body"));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
