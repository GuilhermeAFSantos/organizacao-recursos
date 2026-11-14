package br.senac.qs.recursos.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação da API (RF14). Swagger UI em /swagger-ui.html e especificação em /v3/api-docs.
 * O esquema "bearerAuth" habilita o botão "Authorize" para testar endpoints com JWT.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "Organização de Recursos API",
                version = "v1",
                description = """
                        Alocação de salas, professores e materiais sem conflitos de horário.
                        Erros seguem o padrão Problem Details (RFC 9457), com mensagens em português."""),
        security = @SecurityRequirement(name = OpenApiConfig.ESQUEMA_SEGURANCA))
@SecurityScheme(
        name = OpenApiConfig.ESQUEMA_SEGURANCA,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String ESQUEMA_SEGURANCA = "bearerAuth";
}
