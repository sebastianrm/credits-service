package com.yanki.credits.interfaces.rest;

import com.yanki.credits.domain.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejador global de excepciones para el controlador REST de creditos.
 * Mapea BusinessException a respuestas HTTP con codigo de error.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        HttpStatus status;
        switch (ex.getCode()) {
            case "CREDIT_NOT_FOUND":
            case "PAYMENT_NOT_FOUND":
            case "CUSTOMER_NOT_FOUND":
            case "PAYMENT_NOT_IN_CREDIT":
                status = HttpStatus.NOT_FOUND;
                break;
            case "PERSONAL_CREDIT_LIMIT_EXCEEDED":
            case "CREDIT_ALREADY_PAID":
            case "PAYMENT_REVERSED":
            case "PAYMENT_ALREADY_REVERSED":
                status = HttpStatus.CONFLICT;
                break;
            case "FORBIDDEN_OPERATION":
            case "CUSTOMER_INACTIVE":
            case "CUSTOMER_HAS_OVERDUE_DEBT":
                status = HttpStatus.FORBIDDEN;
                break;
            case "INVALID_PAYMENT_AMOUNT":
            case "INVALID_CREDIT_TYPE":
            case "SCORING_REJECTED":
            case "INVALID_INTEREST_RATE":
                status = HttpStatus.BAD_REQUEST;
                break;
            default:
                log.error("[ExceptionHandler] Código de negocio no mapeado: {} - {}", ex.getCode(), ex.getMessage());
                status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (status == HttpStatus.NOT_FOUND) {
            log.warn("[ExceptionHandler] 404 NOT_FOUND - code={} - message={}", ex.getCode(), ex.getMessage());
        } else if (status == HttpStatus.BAD_REQUEST) {
            log.warn("[ExceptionHandler] 400 BAD_REQUEST - code={} - message={}", ex.getCode(), ex.getMessage());
        } else if (status == HttpStatus.FORBIDDEN) {
            log.warn("[ExceptionHandler] 403 FORBIDDEN - code={} - message={}", ex.getCode(), ex.getMessage());
        } else if (status == HttpStatus.CONFLICT) {
            log.warn("[ExceptionHandler] 409 CONFLICT - code={} - message={}", ex.getCode(), ex.getMessage());
        } else {
            log.error("[ExceptionHandler] 500 INTERNAL_ERROR - code={} - message={}", ex.getCode(), ex.getMessage());
        }

        return ResponseEntity.status(status).body(Map.of(
                "error", ex.getCode(),
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        String stackTrace = java.util.Arrays.stream(ex.getStackTrace())
                .limit(5)
                .map(stack -> "  at " + stack)
                .toString();
        log.error("[ExceptionHandler] Error inesperado no manejado: type={} - message={} - stackTrace={}",
                ex.getClass().getName(), ex.getMessage(), stackTrace, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "Error interno del servidor",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
