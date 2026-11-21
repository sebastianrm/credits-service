package com.yanki.credits.infrastructure.cache.adapter;

import com.yanki.credits.domain.port.out.CachePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.adapter.rxjava.RxJava3Adapter;

/**
 * Adaptador de salida que implementa la caché Redis para créditos.
 * Convierte entre Project Reactor (Mono/Flux) y RxJava 3 (Single/Maybe/Completable).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheRedisAdapter implements CachePort {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Single<Void> set(String key, String value) {
        return RxJava3Adapter.monoToSingle(
                redisTemplate.opsForValue().set(key, value)
                        .doOnSuccess(success -> log.debug("Caché almacenada: {}", key))
                        .then()
        );
    }

    @Override
    public Maybe<String> get(String key) {
        return RxJava3Adapter.monoToMaybe(
                redisTemplate.opsForValue().get(key)
                        .doOnSuccess(value -> {
                            if (value != null) {
                                log.debug("Caché HIT: {}", key);
                            } else {
                                log.debug("Caché MISS: {}", key);
                            }
                        })
        );
    }

    @Override
    public Completable delete(String key) {
        return RxJava3Adapter.monoToCompletable(
                redisTemplate.delete(key)
                        .doOnSuccess(deleted -> log.debug("Caché eliminada: {}", key))
        );
    }
}
