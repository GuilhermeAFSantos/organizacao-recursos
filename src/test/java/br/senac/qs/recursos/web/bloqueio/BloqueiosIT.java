package br.senac.qs.recursos.web.bloqueio;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
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
import org.junit.jupiter.params.provider.CsvSource;
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
 * Bloqueios e manutenção via API (RF08, RC04): cenários MN de docs/testes/bdd-aprovacao-bloqueio.md.
 * Datas relativas a hoje (o encerramento depende do relógio real).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Bloqueios e manutenção via API")
class BloqueiosIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.now(BRASILIA).plusDays(30);

    @Autowired
    TestRestTemplate http;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    String emailAna;
    String ana;
    String rita;
    String adm;
    long sala;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        emailAna = cenario.criarUsuario("SOLICITANTE");
        ana = token(emailAna, "SOLICITANTE");
        rita = token(cenario.criarUsuario("RESPONSAVEL"), "RESPONSAVEL");
        adm = token(cenario.criarUsuario("ADMINISTRADOR"), "ADMINISTRADOR");
        sala = cenario.criarSala(false);
    }

    @Nested
    @DisplayName("Reserva diante de manutenção das 10:00 às 12:00")
    class ReservaDianteDeManutencao {

        @BeforeEach
        void manutencao() {
            assertThat(bloquear(adm, sala, "MANUTENCAO", "10:00", "12:00").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @ParameterizedTest(name = "{0}: reserva [{1}–{2}) → {3}")
        @CsvSource(delimiter = '|', textBlock = """
            F1  | 08:00 | 09:00 | 201
            F3  | 08:00 | 10:00 | 201
            F4  | 08:00 | 10:01 | 409
            F5  | 10:30 | 11:30 | 409
            F6  | 10:00 | 12:00 | 409
            F7  | 09:00 | 13:00 | 409
            F8  | 11:59 | 13:00 | 409
            F9  | 12:00 | 14:00 | 201
            F11 | 13:00 | 14:00 | 201
            """)
        @DisplayName("MN-01: fronteiras (Q1: só encostar não conflita; Q3: minutos cheios)")
        void fronteiras(String caso, String inicio, String fim, int status) {
            assertThat(reservar(ana, sala, inicio, fim).getStatusCode().value()).isEqualTo(status);
        }

        @Test
        @DisplayName("MN-02: a recusa explica que a sala está em manutenção e até quando, sem detalhe interno")
        void mensagemCompreensivel() {
            var resposta = reservar(ana, sala, "10:30", "11:30");

            assertThat(resposta.getBody().get("title").asText()).isEqualTo("Conflito de horário");
            assertThat(resposta.getBody().get("detail").asText())
                    .contains("está em manutenção das 10:00 às 12:00")
                    .doesNotContainIgnoringCase("ocupacao").doesNotContain("23P01").doesNotContain("SQL");
        }

        @Test
        @DisplayName("MN-03: manutenção de uma sala não afeta outra")
        void outraSalaLivre() {
            long outra = cenario.criarSala(false);

            assertThat(reservar(ana, outra, "10:00", "12:00").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("MN-10/Q9: bloqueio ADMINISTRATIVO também impede reservas, com mensagem própria")
        void administrativo() {
            long auditorio = cenario.criarSala(false);
            bloquear(adm, auditorio, "ADMINISTRATIVO", "14:00", "18:00");

            var resposta = reservar(ana, auditorio, "15:00", "16:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody().get("detail").asText()).contains("está bloqueada das 14:00 às 18:00");
        }
    }

    @Nested
    @DisplayName("Registro do bloqueio")
    class Registro {

        @ParameterizedTest(name = "reserva {0}")
        @ValueSource(strings = {"SOLICITADA", "APROVADA", "EM_USO"})
        @DisplayName("MN-06: bloqueio sobre reserva ativa é recusado e lista as reservas em conflito")
        void sobreReservaAtiva(String estado) {
            long recurso = cenario.criarSala(estado.equals("SOLICITADA"));
            long reserva = idReserva(reservar(ana, recurso, "14:00", "16:00"));
            if (estado.equals("EM_USO")) {
                acao(rita, "/api/reservas/" + reserva + "/inicio-uso");
            }
            assertThat(cenario.auditoriaDaReserva(reserva)).last().asString().contains("→" + estado);

            var resposta = bloquear(adm, recurso, "MANUTENCAO", "15:00", "17:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody().get("reservasEmConflito")).extracting(JsonNode::asLong)
                    .containsExactly(reserva);
            assertThat(contarBloqueios(recurso)).isZero();
        }

        @ParameterizedTest(name = "reserva {0}")
        @ValueSource(strings = {"CANCELADA", "REJEITADA", "NAO_COMPARECEU"})
        @DisplayName("MN-07: reservas que já liberaram o horário não impedem o bloqueio")
        void reservaQueLiberouNaoImpede(String estado) {
            long recurso = cenario.criarSala(estado.equals("REJEITADA"));
            switch (estado) {
                case "CANCELADA" -> acao(ana, "/api/reservas/" + idReserva(reservar(ana, recurso, "14:00", "16:00"))
                        + "/cancelamento");
                case "REJEITADA" -> assertThat(http.postForEntity("/api/reservas/"
                        + idReserva(reservar(ana, recurso, "14:00", "16:00")) + "/rejeicao",
                        new HttpEntity<>(Map.of("motivo", "Sem vaga"), cabecalhos(rita)), JsonNode.class)
                        .getStatusCode()).isEqualTo(HttpStatus.OK);
                // Não comparecimento no futuro não existe pela API (só a partir do início): pré-condição no banco.
                default -> cenario.criarReservaNoEstado(emailAna, recurso,
                        java.time.OffsetDateTime.parse(horario("14:00")), java.time.OffsetDateTime.parse(horario("16:00")),
                        estado);
            }

            assertThat(bloquear(adm, recurso, "MANUTENCAO", "15:00", "17:00").getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        @ParameterizedTest(name = "perfil {0} → 403")
        @ValueSource(strings = {"SOLICITANTE", "RESPONSAVEL"})
        @DisplayName("MN-08: só ADMINISTRADOR registra bloqueios")
        void soAdministrador(String perfil) {
            String outro = perfil.equals("SOLICITANTE") ? ana : rita;

            assertThat(bloquear(outro, sala, "MANUTENCAO", "15:00", "17:00").getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("motivo obrigatório e horários em minutos cheios → 400 apontando os campos")
        void validacao() {
            var corpo = corpoBloqueio(sala, "MANUTENCAO", "10:00", "12:00");
            corpo.put("motivo", " ");
            corpo.put("inicio", DIA.atTime(10, 0, 30).atZone(BRASILIA).toOffsetDateTime().toString());

            var resposta = http.postForEntity("/api/bloqueios", new HttpEntity<>(corpo, cabecalhos(adm)), JsonNode.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody().get("erros").findValuesAsText("campo"))
                    .containsExactlyInAnyOrder("inicio", "motivo");
        }

        @Test
        @DisplayName("recurso inexistente → 404")
        void recursoInexistente() {
            var resposta = bloquear(adm, 999_999_999L, "MANUTENCAO", "10:00", "12:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("MN-11/Q10: encerrar manutenção que ainda não começou libera o horário; registro e encerramento auditados")
    void encerramentoLiberaHorario() {
        var bloqueio = bloquear(adm, sala, "MANUTENCAO", "10:00", "12:00");
        long id = bloqueio.getBody().get("id").asLong();

        var resposta = acao(adm, "/api/bloqueios/" + id + "/encerramento");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().get("ativo").asBoolean()).isFalse();
        assertThat(reservar(ana, sala, "10:00", "12:00").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForList("""
                SELECT acao FROM registro_auditoria WHERE entidade = 'BLOQUEIO' AND entidade_id = ? ORDER BY id""",
                String.class, id)).containsExactly("CRIACAO", "ENCERRAMENTO");
    }

    @RepeatedTest(value = 5, name = "rodada {currentRepetition} de {totalRepetitions}")
    @DisplayName("MN-09: reserva e manutenção simultâneas no mesmo horário → só uma é gravada, sem 500")
    void reservaEManutencaoSimultaneas() throws Exception {
        long disputada = cenario.criarSala(false);
        var largada = new CountDownLatch(1);
        List<Integer> status = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> reserva = executor.submit(() -> {
                largada.await();
                return reservar(ana, disputada, "15:00", "16:00").getStatusCode().value();
            });
            Future<Integer> bloqueio = executor.submit(() -> {
                largada.await();
                return bloquear(adm, disputada, "MANUTENCAO", "15:00", "17:00").getStatusCode().value();
            });
            largada.countDown();
            status.add(reserva.get(30, SECONDS));
            status.add(bloqueio.get(30, SECONDS));
        }

        assertThat(status).as("exatamente uma 201 e uma 409").containsExactlyInAnyOrder(201, 409);
        assertThat(cenario.contarOcupacoesBloqueantes(disputada)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ auxiliares

    String token(String email, String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, email, perfil);
    }

    static String horario(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(BRASILIA).toOffsetDateTime().toString();
    }

    static Map<String, Object> corpoBloqueio(long recurso, String tipo, String inicio, String fim) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("recursoId", recurso);
        corpo.put("tipo", tipo);
        corpo.put("motivo", "Troca do projetor");
        corpo.put("inicio", horario(inicio));
        corpo.put("fim", horario(fim));
        return corpo;
    }

    ResponseEntity<JsonNode> bloquear(String token, long recurso, String tipo, String inicio, String fim) {
        return http.postForEntity("/api/bloqueios",
                new HttpEntity<>(corpoBloqueio(recurso, tipo, inicio, fim), cabecalhos(token)), JsonNode.class);
    }

    ResponseEntity<JsonNode> reservar(String token, long recurso, String inicio, String fim) {
        Map<String, Object> corpo = Map.of("salaId", recurso, "inicio", horario(inicio), "fim", horario(fim),
                "finalidade", "Aula");
        return http.postForEntity("/api/reservas", new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    ResponseEntity<JsonNode> acao(String token, String caminho) {
        return http.postForEntity(caminho, new HttpEntity<>(Map.of(), cabecalhos(token)), JsonNode.class);
    }

    static long idReserva(ResponseEntity<JsonNode> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    int contarBloqueios(long recurso) {
        return jdbc.queryForObject("SELECT count(*) FROM ocupacao WHERE recurso_id = ? AND bloqueio_id IS NOT NULL",
                Integer.class, recurso);
    }

    static HttpHeaders cabecalhos(String token) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }
}
