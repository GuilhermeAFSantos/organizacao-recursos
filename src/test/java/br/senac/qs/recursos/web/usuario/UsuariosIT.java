package br.senac.qs.recursos.web.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Gestão de usuários (RF01, RF10) em caixa-preta, com tokens obtidos pelo login real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Gestão de usuários via API")
class UsuariosIT {

    static final String SENHA = "Senha2026";

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    String emailAdm;
    String adm;

    @BeforeEach
    void administradorLogado() {
        cenario = new CenarioBanco(jdbc);
        emailAdm = cenario.criarUsuarioComSenha("ADMINISTRADOR", SENHA);
        adm = tokenDe(emailAdm, SENHA);
    }

    @Nested
    @DisplayName("Cadastro")
    class Cadastro {

        @Test
        @DisplayName("ADMINISTRADOR cadastra; resposta sem senha; e-mail normalizado; o novo usuário consegue entrar; auditado")
        void cadastra() {
            String email = novoEmail();

            var resposta = cadastrar(adm, "Ana", "  " + email.toUpperCase() + " ", SENHA, "SOLICITANTE");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resposta.getBody().get("email").asText()).isEqualTo(email);
            assertThat(resposta.getBody().get("ativo").asBoolean()).isTrue();
            assertThat(resposta.getBody().toString()).doesNotContainIgnoringCase("senha").doesNotContain("$2a$");
            assertThat(login(email, SENHA).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(auditoria(resposta.getBody().get("id").asLong())).containsExactly("CRIACAO");
        }

        @Test
        @DisplayName("e-mail já cadastrado (sem diferenciar maiúsculas) → 409")
        void emailDuplicado() {
            String email = novoEmail();
            cadastrar(adm, "Ana", email, SENHA, "SOLICITANTE");

            var resposta = cadastrar(adm, "Outra Ana", email.toUpperCase(), SENHA, "SOLICITANTE");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody().get("detail").asText())
                    .isEqualTo("Já existe um usuário com o e-mail " + email + ".");
        }

        @Test
        @DisplayName("senha fraca e e-mail inválido → 400 apontando os campos")
        void validacao() {
            var resposta = cadastrar(adm, "Ana", "nao-e-email", "abcdefgh", "SOLICITANTE");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody().get("erros").findValuesAsText("campo"))
                    .containsExactlyInAnyOrder("email", "senha");
        }

        @ParameterizedTest(name = "perfil {0} → 403")
        @ValueSource(strings = {"SOLICITANTE", "RESPONSAVEL"})
        @DisplayName("só ADMINISTRADOR cadastra")
        void soAdministrador(String perfil) {
            String outro = tokenDe(cenario.criarUsuarioComSenha(perfil, SENHA), SENHA);

            assertThat(cadastrar(outro, "Ana", novoEmail(), SENHA, "SOLICITANTE").getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    @DisplayName("listagem para o ADMINISTRADOR, sem expor senha")
    void lista() {
        String email = novoEmail();
        cadastrar(adm, "Bruno", email, SENHA, "RESPONSAVEL");

        var resposta = http.exchange("/api/usuarios", HttpMethod.GET, new HttpEntity<>(cabecalhos(adm)), JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().findValuesAsText("email")).contains(email);
        assertThat(resposta.getBody().toString()).doesNotContainIgnoringCase("senha");
    }

    @Nested
    @DisplayName("Desativação")
    class Desativacao {

        @Test
        @DisplayName("usuário desativado não consegue mais entrar; desativação auditada")
        void desativa() {
            String email = novoEmail();
            long id = cadastrar(adm, "Ana", email, SENHA, "SOLICITANTE").getBody().get("id").asLong();

            var resposta = post(adm, "/api/usuarios/" + id + "/desativacao", Map.of());

            assertThat(resposta.getBody().get("ativo").asBoolean()).isFalse();
            assertThat(login(email, SENHA).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(auditoria(id)).containsExactly("CRIACAO", "DESATIVACAO");
        }

        @Test
        @DisplayName("ADMINISTRADOR não desativa a si mesmo → 422")
        void naoDesativaASiMesmo() {
            long id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, emailAdm);

            var resposta = post(adm, "/api/usuarios/" + id + "/desativacao", Map.of());

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Nested
    @DisplayName("Senhas")
    class Senhas {

        @Test
        @DisplayName("ADMINISTRADOR redefine a senha de outro usuário → 204; a nova senha funciona")
        void administradorRedefine() {
            String email = novoEmail();
            long id = cadastrar(adm, "Ana", email, SENHA, "SOLICITANTE").getBody().get("id").asLong();

            var resposta = put(adm, "/api/usuarios/" + id + "/senha", Map.of("novaSenha", "Nova2026x"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(login(email, "Nova2026x").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("usuário troca a própria senha informando a atual → 204")
        void trocaPropria() {
            String email = cenario.criarUsuarioComSenha("SOLICITANTE", SENHA);
            String token = tokenDe(email, SENHA);

            var resposta = put(token, "/api/usuarios/me/senha", Map.of("senhaAtual", SENHA, "novaSenha", "Nova2026x"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(login(email, "Nova2026x").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("senha atual errada → 422; nova senha fraca → 400")
        void trocaPropriaRecusada() {
            String token = tokenDe(cenario.criarUsuarioComSenha("SOLICITANTE", SENHA), SENHA);

            var atualErrada = put(token, "/api/usuarios/me/senha", Map.of("senhaAtual", "Errada2026", "novaSenha", "Nova2026x"));
            var fraca = put(token, "/api/usuarios/me/senha", Map.of("senhaAtual", SENHA, "novaSenha", "curta1"));

            assertThat(atualErrada.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(atualErrada.getBody().get("detail").asText()).isEqualTo("A senha atual não confere.");
            assertThat(fraca.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    static String novoEmail() {
        return "usuario-" + UUID.randomUUID() + "@senac.br";
    }

    ResponseEntity<JsonNode> login(String email, String senha) {
        return http.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "senha", senha), cabecalhos(null)), JsonNode.class);
    }

    String tokenDe(String email, String senha) {
        var resposta = login(email, senha);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().get("token").asText();
    }

    ResponseEntity<JsonNode> cadastrar(String token, String nome, String email, String senha, String perfil) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", nome);
        corpo.put("email", email);
        corpo.put("senha", senha);
        corpo.put("perfil", perfil);
        return post(token, "/api/usuarios", corpo);
    }

    ResponseEntity<JsonNode> post(String token, String caminho, Map<String, ?> corpo) {
        return http.postForEntity(caminho, new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    ResponseEntity<JsonNode> put(String token, String caminho, Map<String, ?> corpo) {
        return http.exchange(caminho, HttpMethod.PUT, new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    java.util.List<String> auditoria(long usuarioId) {
        return jdbc.queryForList("""
                SELECT acao FROM registro_auditoria WHERE entidade = 'USUARIO' AND entidade_id = ? ORDER BY id""",
                String.class, usuarioId);
    }

    static HttpHeaders cabecalhos(String token) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            cabecalhos.setBearerAuth(token);
        }
        return cabecalhos;
    }
}
