package com.yanki.credits.interfaces.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta estandarizada de error para el microservicio de creditos.
 * Se utiliza en todas las respuestas de error devueltas por GlobalExceptionHandler.
 */
@Schema(description = "Respuesta de error estandarizada del servicio de creditos")
public record ErrorDto(
        @Schema(description = "Codigo identificador del error", example = "CREDIT_NOT_FOUND")
        String error,

        @Schema(description = "Mensaje descriptivo del error en español", example = "Credito no encontrado: cred-123")
        String message,

        @Schema(description = "Marca de tiempo del error en formato ISO-8601", example = "2026-11-14T18:30:00")
        String timestamp
) {
}
