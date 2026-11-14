package br.senac.qs.recursos.web.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Login (RF01) em caixa-preta: senha com BCrypt real, JWT real e PostgreSQL real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("POST /api/auth/login")
class LoginIT {

    static final String SENHA = "Senha2026";

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    String email;

    @BeforeEach
    void usuarioComSenha() {
        cenario = new CenarioBanco(jdbc);
        email = cenario.criarUsuarioComSenha("SOLICITANTE", SENHA);
    }

    @Test
    @DisplayName("credenciais corretas → 200 com JWT que funciona nas rotas protegidas do perfil")
    void loginComSucesso() {
        var resposta = login(email, SENHA);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().get("tipo").asText()).isEqualTo("Bearer");
        assertThat(resposta.getBody().get("perfil").asText()).isEqualTo("SOLICITANTE");
        assertThat(resposta.getBody().has("expiraEm")).isTrue();
        assertThat(resposta.getBody().toString()).doesNotContainIgnoringCase("senha").doesNotContain("$2a$");

        String token = resposta.getBody().get("token").asText();
        assertThat(reservarCom(token).getStatusCode()).as("o token emitido autentica e autoriza")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("e-mail com maiúsculas e espaços é normalizado")
    void emailNormalizado() {
        assertThat(login("  " + email.toUpperCase() + " ", SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("senha errada, e-mail inexistente e usuário inativo → a MESMA resposta 401 (não revela quem existe)")
    void mesmaRespostaParaTodaFalha() {
        var senhaErrada = login(email, "Errada2026");
        var inexistente = login("ninguem-" + email, SENHA);
        String inativo = cenario.criarUsuarioComSenha("SOLICITANTE", SENHA);
        cenario.desativarUsuario(inativo);
        var desativado = login(inativo, SENHA);

        for (var resposta : java.util.List.of(senhaErrada, inexistente, desativado)) {
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(resposta.getBody().get("detail").asText()).isEqualTo("E-mail ou senha inválidos.");
        }
    }

    @Test
    @DisplayName("5 senhas erradas seguidas bloqueiam: a 6ª tentativa, mesmo correta, recebe 429")
    void bloqueioPorForcaBruta() {
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "Errada2026").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        var resposta = login(email, SENHA);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resposta.getBody().get("detail").asText())
                .isEqualTo("Muitas tentativas de login. Tente novamente em alguns minutos.");
        assertThat(jdbc.queryForObject("SELECT bloqueado_ate IS NOT NULL FROM usuario WHERE email = ?",
                Boolean.class, email)).as("bloqueio gravado no banco").isTrue();
    }

    @Test
    @DisplayName("campos obrigatórios → 400 apontando email e senha")
    void validacao() {
        var resposta = http.postForEntity("/api/auth/login", new HttpEntity<>(Map.of(), json()), JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("erros").findValuesAsText("campo")).containsExactlyInAnyOrder("email", "senha");
    }

    // ------------------------------------------------------------------ auxiliares

    ResponseEntity<JsonNode> login(String email, String senha) {
        return http.postForEntity("/api/auth/login", new HttpEntity<>(Map.of("email", email, "senha", senha), json()),
                JsonNode.class);
    }

    ResponseEntity<JsonNode> reservarCom(String token) {
        long sala = cenario.criarSala();
        var cabecalhos = json();
        cabecalhos.setBearerAuth(token);
        Map<String, Object> corpo = Map.of("salaId", sala, "inicio", "2026-12-01T08:00:00-03:00",
                "fim", "2026-12-01T10:00:00-03:00", "finalidade", "Aula");
        return http.postForEntity("/api/reservas", new HttpEntity<>(corpo, cabecalhos), JsonNode.class);
    }

    static HttpHeaders json() {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }
}
