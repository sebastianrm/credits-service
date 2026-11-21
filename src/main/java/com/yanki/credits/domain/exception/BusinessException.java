package com.yanki.credits.domain.exception;

/**
 * Excepción personalizada para manejar errores de negocio en el dominio de créditos.
 * Cada instancia contiene un código de error y un mensaje descriptivo en español.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public class BusinessException extends RuntimeException {
    private final String code;

    /**
     * Construye una excepción de negocio con código y mensaje.
     *
     * @param code código identificador del error
     * @param message mensaje descriptivo del error
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Devuelve el código identificador del error.
     *
     * @return código del error
     */
    public String getCode() {
        return code;
    }
}
