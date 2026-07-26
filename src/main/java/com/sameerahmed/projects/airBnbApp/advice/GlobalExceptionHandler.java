package com.sameerahmed.projects.airBnbApp.advice;

import com.sameerahmed.projects.airBnbApp.exception.ResourceNotFoundException;
import com.sameerahmed.projects.airBnbApp.exception.UnAuthorizedException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildErrorResponseEntity(ApiError.builder()
                .status(HttpStatus.NOT_FOUND)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> subErrors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                subErrors.add(formatFieldError(error)));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                subErrors.add(error.getDefaultMessage()));

        return buildErrorResponseEntity(ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message("Validation failed")
                .subErrors(subErrors)
                .build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return buildErrorResponseEntity(ApiError.builder()
                .status(HttpStatus.CONFLICT)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponseEntity(ApiError.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return buildErrorResponseEntity(ApiError.builder()
                .status(status)
                .message(ex.getReason() != null ? ex.getReason() : ex.getMessage())
                .build());
    }

    @ExceptionHandler({AccessDeniedException.class, UnAuthorizedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(RuntimeException ex) {
        return buildErrorResponseEntity(ApiError.builder()
                .status(HttpStatus.FORBIDDEN)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(Exception ex) {
        return buildErrorResponseEntity(ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtException(JwtException ex) {
        return buildErrorResponseEntity(ApiError.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleInternalServerError(Exception ex) {
        log.error("Unexpected exception occurred", ex);
        return buildErrorResponseEntity(ApiError.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .message(ex.getMessage())
                .build());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponseEntity(ApiError apiError) {
        return ResponseEntity
                .status(apiError.getStatus())
                .body(new ApiResponse<>(apiError));
    }
}
