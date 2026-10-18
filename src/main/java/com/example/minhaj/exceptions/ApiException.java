package com.example.minhaj.exceptions;

import org.springframework.http.HttpStatus;

// Thrown by controllers for expected client errors; GlobalExceptionHandler turns it into a Problem Details response.
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, detail);
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, detail);
    }
}
