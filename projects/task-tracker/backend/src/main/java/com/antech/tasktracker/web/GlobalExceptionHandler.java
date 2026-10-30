package com.antech.tasktracker.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
// Spring Data 4 (Boot 4). On Boot 3 / Spring Data 3 use org.springframework.data.mapping.PropertyReferenceException.
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.antech.tasktracker.service.ConflictException;
import com.antech.tasktracker.service.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/** Maps every error to RFC 7807 ProblemDetail. Never returns stack traces or exception messages from libraries. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://antech.dev/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "One or more fields are invalid", req);
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({ HandlerMethodValidationException.class, ConstraintViolationException.class })
    ProblemDetail handleParamValidation(Exception ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "One or more parameters are invalid", req);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, PropertyReferenceException.class })
    ProblemDetail handleMalformed(Exception ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request",
                "The request body or parameters could not be read", req);
    }

    @ExceptionHandler({ NotFoundException.class, NoResourceFoundException.class })
    ProblemDetail handleNotFound(Exception ex, HttpServletRequest req) {
        String detail = ex instanceof NotFoundException ? ex.getMessage() : "Resource not found";
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", detail, req);
    }

    @ExceptionHandler({ ConflictException.class, ObjectOptimisticLockingFailureException.class,
            DataIntegrityViolationException.class })
    ProblemDetail handleConflict(Exception ex, HttpServletRequest req) {
        String detail = ex instanceof ConflictException ? ex.getMessage()
                : "The resource was modified or violates a uniqueness constraint";
        return problem(HttpStatus.CONFLICT, "conflict", "Conflict", detail, req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed",
                "This HTTP method is not supported here", req);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            // Other standard Spring MVC errors (unsupported media type, not acceptable, ...): keep their 4xx status.
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return problem(status, "request-error", status.getReasonPhrase(), "The request could not be processed", req);
        }
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled error [{}] on {} {}", correlationId, req.getMethod(), req.getRequestURI(), ex);
        ProblemDetail pd = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal error",
                "An unexpected error occurred", req);
        pd.setProperty("correlationId", correlationId);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail,
            HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + type));
        pd.setTitle(title);
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
