package com.priorizasus.priorizasus.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration for PRIORIZASUS.
 *
 * <p>Documents all REST API endpoints under {@code /api/**}. Includes HTTP Basic Auth security
 * scheme — use the <b>Authorize</b> button in Swagger UI with credentials {@code admin} / {@code
 * PRIORIZASUS2026} to call protected endpoints.
 *
 * <p>Swagger UI available at {@code /swagger-ui.html}. OpenAPI JSON spec at {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

  private static final String SECURITY_SCHEME = "basicAuth";

  @Bean
  public GroupedOpenApi restApiEndpoints() {
    return GroupedOpenApi.builder()
        .group("rest-api")
        .displayName("REST API")
        .pathsToMatch("/api/**")
        .build();
  }

  @Bean
  public OpenAPI priorizasusOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("PRIORIZASUS API")
                .description(
                    "API do sistema de agendamento justo e orientado por dados para clínicas ESF"
                        + " (Estratégia Saúde da Família). Classifica pacientes por urgência"
                        + " clínica e dias de atraso na janela de consulta alvo, então aloca 40"
                        + " vagas semanais via seleção algorítmica em lote.")
                .version("0.0.1-SNAPSHOT")
                .contact(
                    new Contact().name("PRIORIZASUS Team").url("https://github.com/priorizasus"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
        .components(
            new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME,
                    new SecurityScheme()
                        .name(SECURITY_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description(
                            "Autenticação HTTP Basic. Use as credenciais de acesso ao sistema.")))
        .servers(
            List.of(
                new Server().url("http://localhost:8080").description("Desenvolvimento local")));
  }
}
