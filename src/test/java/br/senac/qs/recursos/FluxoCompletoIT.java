package br.senac.qs.recursos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Fluxo PONTA A PONTA, só pela API e começando do zero: o único usuário existente é o
 * ADMINISTRADOR criado na inicialização a partir da configuração (app.admin.*).
 *
 * <p>Única preparação fora da API: a sala, porque o cadastro de salas (RF02) ainda não existe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Fluxo ponta a ponta: do primeiro login à reserva concluída e auditada")
class FluxoCompletoIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.now(BRASILIA).plusDays(30);
    static final String SENHA = "Senha2026";

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${app.admin.email}")
    String emailAdmin;

    @Value("${app.admin.senha}")
    String senhaAdmin;

    @Test
    void fluxoCompleto() {
        // 1. O administrador criado na inicialização entra no sistema.
        String admin = token(emailAdmin, senhaAdmin);

        // 2. Ele cadastra uma professora (SOLICITANTE) e uma responsável.
        String emailProfessora = "professora-" + UUID.randomUUID() + "@senac.br";
        String emailResponsavel = "responsavel-" + UUID.randomUUID() + "@senac.br";
        assertThat(cadastrarUsuario(admin, emailProfessora, "SOLICITANTE").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cadastrarUsuario(admin, emailResponsavel, "RESPONSAVEL").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long laboratorio = new CenarioBanco(jdbc).criarSala(true);

        // 3. A professora entra e reserva o laboratório (restrito): fica aguardando aprovação.
        String professora = token(emailProfessora, SENHA);
        var reserva = post(professora, "/api/reservas", Map.of("salaId", laboratorio,
                "inicio", horario(8), "fim", horario(10), "finalidade", "Aula prática"));
        assertThat(reserva.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reserva.getBody().get("status").asText()).isEqualTo("SOLICITADA");
        long id = reserva.getBody().get("id").asLong();

        // 4. A responsável entra, aprova, registra o uso e conclui.
        String responsavel = token(emailResponsavel, SENHA);
        assertThat(status(post(responsavel, "/api/reservas/" + id + "/aprovacao", Map.of()))).isEqualTo("APROVADA");
        assertThat(status(post(responsavel, "/api/reservas/" + id + "/inicio-uso", Map.of()))).isEqualTo("EM_USO");
        assertThat(status(post(responsavel, "/api/reservas/" + id + "/conclusao", Map.of()))).isEqualTo("CONCLUIDA");

        // 5. O administrador bloqueia o laboratório para manutenção à tarde; a professora não consegue reservar.
        var bloqueio = post(admin, "/api/bloqueios", Map.of("recursoId", laboratorio, "tipo", "MANUTENCAO",
                "motivo", "Troca das bancadas", "inicio", horario(14), "fim", horario(18)));
        assertThat(bloqueio.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var recusada = post(professora, "/api/reservas", Map.of("salaId", laboratorio,
                "inicio", horario(15), "fim", horario(16), "finalidade", "Reposição"));
        assertThat(recusada.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(recusada.getBody().get("detail").asText()).contains("está em manutenção");

        // 6. A trilha de auditoria da reserva está completa, com quem fez cada mudança.
        List<String> trilha = jdbc.queryForList("""
                SELECT coalesce(estado_anterior, '—') || '→' || estado_novo || ' por ' || usuario_email
                FROM registro_auditoria WHERE entidade = 'RESERVA' AND entidade_id = ? ORDER BY id""", String.class, id);
        assertThat(trilha).containsExactly(
                "—→SOLICITADA por " + emailProfessora,
                "SOLICITADA→APROVADA por " + emailResponsavel,
                "APROVADA→EM_USO por " + emailResponsavel,
                "EM_USO→CONCLUIDA por " + emailResponsavel);
    }

    // ------------------------------------------------------------------ auxiliares

    static String horario(int hora) {
        return DIA.atTime(hora, 0).atZone(BRASILIA).toOffsetDateTime().toString();
    }

    String token(String email, String senha) {
        var resposta = http.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "senha", senha), cabecalhos(null)), JsonNode.class);
        assertThat(resposta.getStatusCode()).as("login de " + email).isEqualTo(HttpStatus.OK);
        return resposta.getBody().get("token").asText();
    }

    ResponseEntity<JsonNode> cadastrarUsuario(String token, String email, String perfil) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", "Pessoa " + perfil);
        corpo.put("email", email);
        corpo.put("senha", SENHA);
        corpo.put("perfil", perfil);
        return post(token, "/api/usuarios", corpo);
    }

    ResponseEntity<JsonNode> post(String token, String caminho, Map<String, ?> corpo) {
        return http.postForEntity(caminho, new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    static String status(ResponseEntity<JsonNode> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().get("status").asText();
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
