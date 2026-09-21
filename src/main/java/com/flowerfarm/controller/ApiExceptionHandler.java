package com.flowerfarm.controller;

import com.flowerfarm.security.SecretRedactor;
import com.flowerfarm.service.AttachmentTooLargeException;
import com.flowerfarm.service.InventoryCountConflictException;
import com.flowerfarm.service.ProductionConflictException;
import com.flowerfarm.service.WorkerActionConflictException;
import com.flowerfarm.service.WorkerOrderConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Consistent, non-sensitive JSON errors for REST endpoints.
 *
 * <p>The legacy {@code error} field remains present for API compatibility;
 * {@code code}, {@code status}, {@code path}, and {@code timestamp} make errors
 * actionable for newer clients. Unexpected exception details are redacted before
 * logging and are never returned to callers.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final int MAX_PUBLIC_MESSAGE_LENGTH = 240;

    @ExceptionHandler(IndexOutOfBoundsException.class)
    public ResponseEntity<Map<String, Object>> notFound(
            IndexOutOfBoundsException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND",
                publicMessage(exception, "The requested record was not found."), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, Object>> badRequest(
            Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                publicMessage(exception, "The request contains an invalid value."), request);
    }

    @ExceptionHandler({AttachmentTooLargeException.class,
            MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, Object>> attachmentTooLarge(
            Exception exception, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE",
                publicMessage(exception, "Attachment file cannot exceed 15 MiB."), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedBody(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_BODY",
                "Request body is malformed or contains invalid values.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> unsupportedMediaType(HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "The request Content-Type is not supported for this route.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> unacceptableResponseType() {
        // The client cannot accept our JSON error body, so do not negotiate it again.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> beanValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("The request contains invalid values.");
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<Map<String, Object>> conflict(
            RuntimeException exception, HttpServletRequest request) {
        log.warn("Persistence conflict on {} {}: {}", request.getMethod(),
                request.getRequestURI(), exception.getClass().getSimpleName());
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The record changed or conflicts with existing data. Refresh and try again.", request);
    }

    @ExceptionHandler(ProductionConflictException.class)
    public ResponseEntity<Map<String, Object>> productionConflict(
            ProductionConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "PRODUCTION_CONFLICT",
                publicMessage(exception, "The production change conflicts with existing data."),
                request);
    }

    @ExceptionHandler(WorkerActionConflictException.class)
    public ResponseEntity<Map<String, Object>> workerActionConflict(
            WorkerActionConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "WORKER_ACTION_CONFLICT",
                publicMessage(exception, "The worker operation key is already in use."),
                request);
    }

    @ExceptionHandler(InventoryCountConflictException.class)
    public ResponseEntity<Map<String, Object>> inventoryCountConflict(
            InventoryCountConflictException exception, HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> result = response(
                HttpStatus.CONFLICT, "INVENTORY_COUNT_CONFLICT",
                publicMessage(exception, "Inventory changed after the count was reviewed."),
                request);
        Objects.requireNonNull(result.getBody(), "API error body")
                .put("currentQuantity", exception.currentQuantity());
        return result;
    }

    @ExceptionHandler(WorkerOrderConflictException.class)
    public ResponseEntity<Map<String, Object>> workerOrderConflict(
            WorkerOrderConflictException exception, HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> result = response(
                HttpStatus.CONFLICT, "ORDER_HANDOFF_CONFLICT",
                publicMessage(exception, "The order changed after the handoff was reviewed."),
                request);
        Objects.requireNonNull(result.getBody(), "API error body")
                .put("orderId", exception.orderId());
        return result;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> routeNotFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND",
                "The requested API route was not found.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodNotAllowed(HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This HTTP method is not supported for the requested route.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(
            Exception exception, HttpServletRequest request) {
        String incidentId = UUID.randomUUID().toString();
        String safeDetail = SecretRedactor.redact(exception.getClass().getSimpleName()
                + (exception.getMessage() == null ? "" : ": " + exception.getMessage()), 500);
        log.error("Unhandled API failure {} on {} {}: {}", incidentId,
                request.getMethod(), request.getRequestURI(), safeDetail);
        ResponseEntity<Map<String, Object>> result = response(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The operation could not be completed. Give support this incident ID: "
                        + incidentId,
                request);
        Objects.requireNonNull(result.getBody(), "API error body").put("incidentId", incidentId);
        return result;
    }

    private static ResponseEntity<Map<String, Object>> response(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", SecretRedactor.redact(
                message == null ? "Request failed." : message, MAX_PUBLIC_MESSAGE_LENGTH));
        body.put("code", code);
        body.put("status", status.value());
        body.put("path", request.getRequestURI());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    private static String publicMessage(Exception exception, String fallback) {
        return exception == null || exception.getMessage() == null
                || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }

}
