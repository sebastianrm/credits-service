package com.yanki.credits.domain.port.out;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * Puerto de salida para operaciones de caché Redis.
 * Permite almacenar y recuperar datos de crédito con TTL configurable.
 *
 * @author Sebastian Retamal
 * @contact semorale@emeal.nttdata.com
 */
public interface CachePort {

    /**
     * Almacena un valor en la caché con la clave indicada.
     *
     * @param key clave de la caché
     * @param value valor a almacenar
     * @return single cuando la operación completa
     */
    Single<Void> set(String key, String value);

    /**
     * Recupera un valor de la caché por su clave.
     *
     * @param key clave de la caché
     * @return valor encontrado o vacío
     */
    Maybe<String> get(String key);

    /**
     * Elimina una clave de la caché.
     *
     * @param key clave a eliminar
     * @return completable cuando la operación finaliza
     */
    Completable delete(String key);
}
