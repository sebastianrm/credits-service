package com.yanki.credits.domain.exception;

/**
 * Excepción lanzada cuando un crédito no se encuentra por su identificador.
 */
public class CreditNotFoundException extends BusinessException {

    public CreditNotFoundException(String id) {
        super("CREDIT_NOT_FOUND", "Crédito no encontrado: " + id);
    }
}
