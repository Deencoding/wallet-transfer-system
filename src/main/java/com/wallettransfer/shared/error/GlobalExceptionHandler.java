package com.wallettransfer.shared.error;

import com.wallettransfer.shared.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .min(Comparator.<FieldError>comparingInt(error -> fieldPriority(error.getField()))
                        .thenComparingInt(error -> constraintPriority(error.getCode())))
                .map(error -> validationMessage(error.getField(), error.getDefaultMessage()))
                .orElse("Request validation failed");
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedJson(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is malformed");
    }

    @ExceptionHandler({
        MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class
    })
    ResponseEntity<ApiError> malformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Required request data is missing or invalid");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> methodValidation(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Request validation failed");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMethod(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The request method is not supported");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiError> unacceptableMediaType(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "The requested response media type is not supported");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMediaType(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "The request content type is not supported");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You are not permitted to perform this action");
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> domain(DomainException exception, HttpServletRequest request) {
        HttpStatus status =
                switch (exception.code()) {
                    case USER_NOT_FOUND,
                            WALLET_NOT_FOUND,
                            TRANSFER_NOT_FOUND,
                            RECONCILIATION_CASE_NOT_FOUND,
                            LEDGER_ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case EMAIL_ALREADY_REGISTERED,
                            WALLET_ALREADY_EXISTS,
                            INVALID_WALLET_STATE_TRANSITION,
                            WALLET_HAS_BALANCE,
                            CONCURRENT_WALLET_UPDATE,
                            INSUFFICIENT_FUNDS,
                            SENDER_WALLET_UNAVAILABLE,
                            RECEIVER_WALLET_UNAVAILABLE,
                            CURRENCY_MISMATCH,
                            LEDGER_CURRENCY_MISMATCH,
                            INVALID_TRANSFER_STATE,
                            WALLET_BUSY,
                            IDEMPOTENCY_KEY_CONFLICT,
                            TRANSFER_NOT_REVERSIBLE,
                            REVERSAL_ALREADY_EXISTS,
                            RECONCILIATION_ALREADY_RUNNING,
                            RECONCILIATION_REPAIR_CONFLICT,
                            UNSAFE_RECONCILIATION_REPAIR -> HttpStatus.CONFLICT;
                    case SAME_WALLET_TRANSFER, INVALID_TRANSFER_AMOUNT, UNBALANCED_JOURNAL, INVALID_LEDGER_ENTRY ->
                        HttpStatus.UNPROCESSABLE_ENTITY;
                    case AUTHENTICATION_FAILED, INVALID_CREDENTIALS, INVALID_TOKEN, REFRESH_TOKEN_REVOKED ->
                        HttpStatus.UNAUTHORIZED;
                    case INVALID_IDEMPOTENCY_KEY -> HttpStatus.BAD_REQUEST;
                };
        return response(status, exception.code().name(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected request failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed");
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
        ApiError error = new ApiError(code, message);
        return ResponseEntity.status(status).body(error);
    }

    private int fieldPriority(String field) {
        return switch (field) {
            case "email" -> 0;
            case "password" -> 1;
            default -> 2;
        };
    }

    private int constraintPriority(String constraint) {
        if (constraint == null) {
            return Integer.MAX_VALUE;
        }
        return switch (constraint) {
            case "NotBlank", "NotNull", "NotEmpty" -> 0;
            case "Size" -> 1;
            case "Email" -> 2;
            default -> 3;
        };
    }

    private String validationMessage(String field, String message) {
        String subject = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        if (message != null && message.startsWith(subject + " ")) {
            return message;
        }
        if ("must not be blank".equals(message)
                || "must not be empty".equals(message)
                || "must not be null".equals(message)) {
            return subject + " is required";
        }
        if ("must be a well-formed email address".equals(message)) {
            return subject + " must be a valid email address";
        }
        return subject + " " + message;
    }
}
