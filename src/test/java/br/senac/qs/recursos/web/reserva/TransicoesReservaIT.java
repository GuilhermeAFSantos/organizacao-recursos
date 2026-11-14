package br.senac.qs.recursos.web.reserva;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.GeradorTokenTeste;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * API das transições de estado (RF07, RC05, RC06, RC08) em caixa-preta: HTTP, JWT e PostgreSQL reais.
 *
 * <p>Datas RELATIVAS a hoje: a API usa o relógio real e as regras dependem do horário ("aprovar só
 * antes do início"). Datas fixas fariam o teste quebrar sozinho quando o calendário passasse delas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Transições de estado da reserva via API")
class TransicoesReservaIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DAQUI_30_DIAS = LocalDate.now(BRASILIA).plusDays(30);
    static final LocalDate HA_30_DIAS = LocalDate.now(BRASILIA).minusDays(30);

    @Autowired
    TestRestTemplate http;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    String emailAna;
    String ana;
    String bruno;
    String rita;
    String adm;
    long laboratorio;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        emailAna = cenario.criarUsuario("SOLICITANTE");
        ana = token(emailAna, "SOLICITANTE");
        bruno = token(cenario.criarUsuario("SOLICITANTE"), "SOLICITANTE");
        rita = token(cenario.criarUsuario("RESPONSAVEL"), "RESPONSAVEL");
        adm = token(cenario.criarUsuario("ADMINISTRADOR"), "ADMINISTRADOR");
        laboratorio = cenario.criarSala(true);
    }

    @Nested
    @DisplayName("Aprovação e rejeição (RF07, RC06)")
    class AprovacaoERejeicao {

        @Test
        @DisplayName("AP-04/RC08: RESPONSAVEL aprova; a transição vai para a auditoria")
        void responsavelAprova() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            var resposta = acao(rita, reserva, "aprovacao", null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody().get("status").asText()).isEqualTo("APROVADA");
            assertThat(cenario.auditoriaDaReserva(reserva)).hasSize(2)
                    .last().asString().startsWith("SOLICITADA→APROVADA por responsavel-");
        }

        @ParameterizedTest(name = "perfil {0} → 403")
        @ValueSource(strings = {"SOLICITANTE", "ADMINISTRADOR"})
        @DisplayName("AP-05: só o perfil RESPONSAVEL aprova")
        void soResponsavelAprova(String perfil) {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);
            String outro = token(cenario.criarUsuario(perfil), perfil);

            var resposta = acao(outro, reserva, "aprovacao", null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(cenario.auditoriaDaReserva(reserva)).hasSize(1);
        }

        @Test
        @DisplayName("AU-01: a dona da reserva não aprova a própria reserva")
        void donaNaoAprova() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            assertThat(acao(ana, reserva, "aprovacao", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("AU-02: token adulterado (assinado com outra chave) → 401")
        void tokenAdulterado() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);
            String forjado = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbmEiLCJwZXJmaWwiOiJSRVNQT05TQVZFTCJ9."
                    + "YXNzaW5hdHVyYS1mb3JqYWRhLWNvbS1vdXRyYS1jaGF2ZS0wMTIzNDU2Nzg5";

            assertThat(acao(forjado, reserva, "aprovacao", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("AP-06: rejeição com motivo libera o horário para outra pessoa")
        void rejeicaoLiberaHorario() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            var resposta = acao(rita, reserva, "rejeicao", "Laboratório reservado para prova");

            assertThat(resposta.getBody().get("status").asText()).isEqualTo("REJEITADA");
            assertThat(reservar(bruno, laboratorio, DAQUI_30_DIAS).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("AP-07: rejeição sem motivo → 400 apontando o campo motivo")
        void rejeicaoSemMotivo() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            var resposta = acao(rita, reserva, "rejeicao", " ");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody().get("erros").findValuesAsText("campo")).containsExactly("motivo");
        }
    }

    @Nested
    @DisplayName("Cancelamento")
    class Cancelamento {

        @Test
        @DisplayName("a dona cancela; o horário fica livre")
        void donaCancela() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            var resposta = acao(ana, reserva, "cancelamento", "Aula remarcada");

            assertThat(resposta.getBody().get("status").asText()).isEqualTo("CANCELADA");
            assertThat(cenario.contarOcupacoesBloqueantes(laboratorio)).isZero();
        }

        @Test
        @DisplayName("ES-03: outro solicitante não cancela reserva alheia → 403 com mensagem em português")
        void outroNaoCancela() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            var resposta = acao(bruno, reserva, "cancelamento", null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(resposta.getBody().get("detail").asText())
                    .isEqualTo("Somente quem solicitou a reserva ou um ADMINISTRADOR pode cancelá-la.");
        }

        @Test
        @DisplayName("ADMINISTRADOR cancela reserva de outra pessoa")
        void administradorCancela() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            assertThat(acao(adm, reserva, "cancelamento", "Evento").getBody().get("status").asText())
                    .isEqualTo("CANCELADA");
        }
    }

    @Nested
    @DisplayName("Fluxo e transições inválidas (RC05)")
    class Fluxo {

        @Test
        @DisplayName("ES-01: SOLICITADA → APROVADA → EM_USO → CONCLUIDA, com 4 registros de auditoria")
        void fluxoPrincipal() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            acao(rita, reserva, "aprovacao", null);
            acao(rita, reserva, "inicio-uso", null);
            var resposta = acao(rita, reserva, "conclusao", null);

            assertThat(resposta.getBody().get("status").asText()).isEqualTo("CONCLUIDA");
            assertThat(cenario.auditoriaDaReserva(reserva)).hasSize(4);
        }

        @Test
        @DisplayName("Q5: não comparecimento registrado depois do início libera o horário")
        void naoComparecimento() {
            long salaComum = cenario.criarSala(false);
            // No passado só pelo banco: a API não cria reserva que já começou (Q12).
            long reserva = cenario.criarReservaAprovadaNoPassado(emailAna, salaComum, HA_30_DIAS);

            var resposta = acao(rita, reserva, "nao-comparecimento", "Ninguém compareceu");

            assertThat(resposta.getBody().get("status").asText()).isEqualTo("NAO_COMPARECEU");
            assertThat(cenario.contarOcupacoesBloqueantes(salaComum)).isZero();
        }

        @Test
        @DisplayName("ES-02: pular a aprovação → 422 com mensagem que explica as opções")
        void pularAprovacao() {
            long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);

            var resposta = acao(rita, reserva, "inicio-uso", null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody().get("detail").asText())
                    .startsWith("Não é possível mudar a reserva de SOLICITADA para EM_USO.");
            assertThat(cenario.auditoriaDaReserva(reserva)).as("recusa não gera auditoria").hasSize(1);
        }

        @Test
        @DisplayName("reserva inexistente → 404")
        void inexistente() {
            var resposta = acao(rita, 999_999_999L, "aprovacao", null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(resposta.getBody().get("detail").asText()).isEqualTo("Reserva 999999999 não encontrada.");
        }
    }

    @RepeatedTest(value = 5, name = "rodada {currentRepetition} de {totalRepetitions}")
    @DisplayName("ES-05: aprovar e cancelar ao mesmo tempo → só uma vence, sem 500, com 1 transição auditada")
    void decisoesSimultaneas() throws Exception {
        long reserva = solicitar(ana, laboratorio, DAQUI_30_DIAS);
        var largada = new CountDownLatch(1);
        List<Integer> status = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> aprovar = executor.submit(() -> {
                largada.await();
                return acao(rita, reserva, "aprovacao", null).getStatusCode().value();
            });
            Future<Integer> cancelar = executor.submit(() -> {
                largada.await();
                return acao(ana, reserva, "cancelamento", null).getStatusCode().value();
            });
            largada.countDown();
            status.add(aprovar.get(30, SECONDS));
            status.add(cancelar.get(30, SECONDS));
        }

        assertThat(status).as("uma vence (200); a outra perde com 409 ou 422").containsOnlyOnce(200)
                .allMatch(s -> s == 200 || s == 409 || s == 422);
        assertThat(cenario.auditoriaDaReserva(reserva)).as("criação + 1 transição").hasSize(2);
    }

    // ------------------------------------------------------------------ auxiliares

    String token(String email, String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, email, perfil);
    }

    ResponseEntity<JsonNode> reservar(String token, long sala, LocalDate dia) {
        Map<String, Object> corpo = Map.of("salaId", sala,
                "inicio", dia.atTime(8, 0).atZone(BRASILIA).toOffsetDateTime().toString(),
                "fim", dia.atTime(10, 0).atZone(BRASILIA).toOffsetDateTime().toString(),
                "finalidade", "Aula");
        return http.postForEntity("/api/reservas", new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    long solicitar(String token, long sala, LocalDate dia) {
        var resposta = reservar(token, sala, dia);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    ResponseEntity<JsonNode> acao(String token, long reserva, String acao, String motivo) {
        Map<String, Object> corpo = new HashMap<>();
        if (motivo != null) {
            corpo.put("motivo", motivo);
        }
        return http.postForEntity("/api/reservas/" + reserva + "/" + acao,
                new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    static HttpHeaders cabecalhos(String token) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }
}
