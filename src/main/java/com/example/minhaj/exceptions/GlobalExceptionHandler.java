package com.example.minhaj.exceptions;

import com.example.minhaj.config.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.beans.TypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

// Every error leaves the API as RFC 9457 Problem Details (application/problem+json).
// The base class covers the Spring MVC exceptions; the overrides below only replace their detail messages.
// Every body carries the request's ID as "requestId" (same as the X-Request-Id header) so users can quote it.
// Spring Security's 401/403 come through here too, via SecurityProblemHandler.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI BLANK_TYPE = URI.create("about:blank");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.putIfAbsent(field, error.getDefaultMessage());
        });

        ProblemDetail body = problemDetail(status, "Request validation failed.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    // Framework errors caused by the client; without these the catch-all below turned them into 500s.
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return problem(ex, status, "Malformed or unreadable request body.", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        return problem(ex, status, "Invalid value '" + ex.getValue() + "' for parameter '" + name + "'.", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        return problem(ex, status, "Not found: /" + ex.getResourcePath(), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers, HttpStatusCode status,
                                                                         WebRequest request) {
        return problem(ex, status, "Method " + ex.getMethod() + " is not supported for this endpoint.", headers, request);
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return problemDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problemDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return problemDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problemDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // Reached from SecurityProblemHandler when a protected endpoint is called without valid credentials.
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problemDetail(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralExceptions(Exception ex) {
        log.error("Unhandled exception", ex);
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our end!");
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatusCode status, String detail,
                                           HttpHeaders headers, WebRequest request) {
        return handleExceptionInternal(ex, problemDetail(status, detail), headers, status, request);
    }

    // Also covers the base-class handlers whose bodies are built by Spring (e.g. 415, missing parameter).
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getType() == null) {
                problem.setType(BLANK_TYPE);
            }
            addRequestId(problem);
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    // Spring 7 omits "type" when it is null (meaning about:blank); set it so every error carries all five fields.
    private static ProblemDetail problemDetail(HttpStatusCode status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(BLANK_TYPE);
        addRequestId(problem);
        return problem;
    }

    private static void addRequestId(ProblemDetail problem) {
        String requestId = RequestIdFilter.current();
        if (requestId != null && (problem.getProperties() == null || !problem.getProperties().containsKey("requestId"))) {
            problem.setProperty("requestId", requestId);
        }
    }
}
