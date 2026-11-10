package com.inversioneslr.marketplace.compartido.presentacion;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * SCRUM-99: datos generales de la documentación OpenAPI. Con "Authorize" se pega el token de
 * POST /api/v1/auth/login y Swagger UI lo envía como Bearer en cada solicitud.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Marketplace de alojamientos - API", version = "v1",
                description = "API de Inversiones LR. Los endpoints con candado exigen un token JWT."),
        security = @SecurityRequirement(name = "jwt"))
@SecurityScheme(name = "jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class ConfiguracionOpenApi {
}
