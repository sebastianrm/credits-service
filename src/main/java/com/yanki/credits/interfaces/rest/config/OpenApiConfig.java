package com.yanki.credits.interfaces.rest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración global de OpenAPI 3 para el microservicio de créditos.
 * Define la información del servidor, los entornos disponibles y los metadatos del documento.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI creditsServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Yanki Credits Service API")
                        .version("1.0.0")
                        .description(
                                "API REST reactiva del microservicio de créditos de la plataforma bancaria Yanki.\n"
                                + "\n"
                                + "Este servicio gestiona el ciclo de vida completo de los créditos:\n"
                                + "- Creación de créditos personales y empresariales con scoring y validación de reglas de negocio\n"
                                + "- Generación automática de cronograma de pagos (sistema francés - cuotas fijas)\n"
                                + "- Registro de pagos con aplicación priorizada (mora, interés, capital)\n"
                                + "- Consulta de créditos y cronogramas con caché Redis\n"
                                + "- Detección de mora y bloqueo transversal mediante job programado\n"
                                + "\n"
                                + "Arquitectura: Hexagonal con puertos y adaptadores.\n"
                                + "Stack: Spring WebFlux (reactivo), MongoDB, Redis, Kafka, RxJava 3."
                        )
                        .contact(new Contact()
                                .name("Equipo Yanki Platform")
                                .email("yanki-platform@yanki.com"))
                        .license(new License()
                                .name("Propietario")
                                .url("https://yanki.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8084")
                                .description("Entorno local de desarrollo"),
                        new Server()
                                .url("http://credits-service:8084")
                                .description("Entorno Docker/Kubernetes"),
                        new Server()
                                .url("https://api-staging.yanki.com/credits")
                                .description("Entorno de staging"),
                        new Server()
                                .url("https://api.yanki.com/credits")
                                .description("Entorno de producción")
                ));
    }
}
