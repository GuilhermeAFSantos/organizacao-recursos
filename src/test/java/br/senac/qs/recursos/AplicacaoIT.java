package br.senac.qs.recursos;

import static org.assertj.core.api.Assertions.assertThat;

import br.senac.qs.recursos.suporte.GeradorTokenTeste;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sobe a aplicação COMPLETA (servidor HTTP real em porta aleatória + PostgreSQL via
 * Testcontainers + Flyway) e verifica a infraestrutura de ponta a ponta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Aplicação completa (HTTP real + PostgreSQL)")
class AplicacaoIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("RF14: especificação OpenAPI publicada, com esquema de segurança JWT")
    void openApiPublicado() {
        var resposta = http.getForEntity("/v3/api-docs", JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().at("/info/title").asText()).isEqualTo("Organização de Recursos API");
        assertThat(resposta.getBody().at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
    }

    @Test
    @DisplayName("RF14: Swagger UI acessível sem autenticação")
    void swaggerUiAcessivel() {
        var resposta = http.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("swagger-ui");
    }

    @Test
    @DisplayName("health check responde UP (banco acessível)")
    void healthCheck() {
        var resposta = http.getForEntity("/actuator/health", JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("RF13: página estática inicial é servida")
    void paginaInicial() {
        var resposta = http.getForEntity("/", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Organização de Recursos").contains("viewport");
    }

    @Test
    @DisplayName("RF01: API protegida responde 401 em ProblemDetail sem token")
    void apiExigeToken() {
        var resposta = http.getForEntity("/api/reservas", JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(resposta.getBody().get("title").asText()).isEqualTo("Não autenticado");
    }

    @Test
    @DisplayName("RF01: token assinado com outra chave é rejeitado")
    void tokenDeOutraChaveRejeitado() {
        String tokenForjado = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhdGFjYW50ZSIsInBlcmZpbCI6IkFETUlOSVNUUkFET1IifQ."
                + "c2VtLWFzc2luYXR1cmEtdmFsaWRhLXNlbS1hc3NpbmF0dXJhLXZhbGlkYQ";

        var resposta = http.exchange("/api/reservas", HttpMethod.GET, comToken(tokenForjado), JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("RF01: token válido passa pela segurança (rota inexistente -> 404 em português)")
    void tokenValidoAceito() {
        String token = GeradorTokenTeste.token(jwtEncoder, "ana@senac.br", "SOLICITANTE");

        var resposta = http.exchange("/api/rota-inexistente", HttpMethod.GET, comToken(token), JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().get("detail").asText()).isEqualTo("O endereço solicitado não existe.");
    }

    private static HttpEntity<Void> comToken(String token) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        return new HttpEntity<>(cabecalhos);
    }
}
