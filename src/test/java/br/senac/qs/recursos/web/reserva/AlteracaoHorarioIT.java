package br.senac.qs.recursos.web.reserva;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/** Alteração de horário via API (RF04), incluindo MN-05 e a agenda do professor (RF05). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Alteração de horário via API")
class AlteracaoHorarioIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.now(BRASILIA).plusDays(30);
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
    long sala;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        emailAna = cenario.criarUsuario("SOLICITANTE");
        ana = GeradorTokenTeste.token(jwtEncoder, emailAna, "SOLICITANTE");
        bruno = token("SOLICITANTE");
        rita = token("RESPONSAVEL");
        adm = token("ADMINISTRADOR");
        sala = cenario.criarSala(false);
    }

    @Nested
    @DisplayName("Alteração aceita")
    class Aceita {

        @Test
        @DisplayName("a dona move a reserva: 200, horário novo, o antigo fica livre e a mudança é auditada")
        void moveELibera() {
            long reserva = reservar(ana, sala, null, "10:00", "12:00");

            var resposta = alterar(ana, reserva, "14:00", "16:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(instante(resposta, "inicio")).isEqualTo(horario("14:00"));
            assertThat(instante(resposta, "fim")).isEqualTo(horario("16:00"));
            assertThat(resposta.getBody().get("status").asText()).isEqualTo("APROVADA");
            assertThat(periodoGravado(reserva)).containsExactly(horario("14:00"), horario("16:00"));
            assertThat(reservarStatus(bruno, sala, "10:00", "12:00")).as("horário antigo livre").isEqualTo(201);
            assertThat(acoesAuditadas(reserva)).last().isEqualTo("ALTERACAO por " + emailAna
                    + ": Horário alterado de " + DIA.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " 10:00–12:00 para " + DIA.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " 14:00–16:00.");
        }

        @Test
        @DisplayName("deslocar por cima do próprio horário não conflita consigo mesma; encostar em outra reserva é aceito")
        void sobreOProprioHorario() {
            long reserva = reservar(ana, sala, null, "10:00", "12:00");
            reservar(bruno, sala, null, "13:00", "14:00");

            assertThat(alterar(ana, reserva, "11:00", "13:00").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("o ADMINISTRADOR altera a reserva de outra pessoa")
        void administrador() {
            long reserva = reservar(ana, sala, null, "10:00", "12:00");

            assertThat(alterar(adm, reserva, "14:00", "16:00").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("laboratório aprovado volta a SOLICITADA e precisa de nova aprovação")
        void reaprovacao() {
            long laboratorio = cenario.criarSala(true);
            long reserva = reservar(ana, laboratorio, null, "10:00", "12:00");
            assertThat(acao(rita, reserva, "aprovacao").getStatusCode()).isEqualTo(HttpStatus.OK);

            var alterada = alterar(ana, reserva, "14:00", "16:00");
            var reaprovada = acao(rita, reserva, "aprovacao");

            assertThat(alterada.getBody().get("status").asText()).isEqualTo("SOLICITADA");
            assertThat(reaprovada.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(reaprovada.getBody().get("status").asText()).isEqualTo("APROVADA");
        }
    }

    @Nested
    @DisplayName("Alteração recusada, sem mudar nada")
    class Recusada {

        @Test
        @DisplayName("MN-05: para dentro da manutenção → 409 explicando; a reserva mantém o horário")
        void dentroDaManutencao() {
            long reserva = reservar(ana, sala, null, "13:00", "14:00");
            var manutencao = http.postForEntity("/api/bloqueios", new HttpEntity<>(Map.of("recursoId", sala,
                    "tipo", "MANUTENCAO", "motivo", "Pintura", "inicio", texto("10:00"), "fim", texto("12:00")),
                    cabecalhos(adm)), JsonNode.class);
            assertThat(manutencao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            var resposta = alterar(ana, reserva, "11:00", "12:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody().get("detail").asText()).contains("está em manutenção das 10:00 às 12:00");
            assertThat(periodoGravado(reserva)).containsExactly(horario("13:00"), horario("14:00"));
        }

        @Test
        @DisplayName("para o horário de outra reserva da sala → 409")
        void outraReserva() {
            long reserva = reservar(ana, sala, null, "10:00", "12:00");
            reservar(bruno, sala, null, "14:00", "16:00");

            assertThat(alterar(ana, reserva, "15:00", "17:00").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(periodoGravado(reserva)).containsExactly(horario("10:00"), horario("12:00"));
        }

        @Test
        @DisplayName("RF05: o professor da reserva está ocupado no novo horário → 409 citando o professor")
        void professorOcupado() {
            long professor = professor();
            long reserva = reservar(ana, sala, professor, "10:00", "12:00");
            reservar(bruno, cenario.criarSala(false), professor, "14:00", "16:00");

            var resposta = alterar(ana, reserva, "15:00", "17:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody().get("detail").asText()).startsWith("O professor ");
            assertThat(contarOcupacoesNoPeriodo(reserva, "10:00", "12:00")).as("sala e professor").isEqualTo(2);
        }

        @Test
        @DisplayName("outro solicitante → 403; RESPONSAVEL → 403")
        void semPermissao() {
            long reserva = reservar(ana, sala, null, "10:00", "12:00");

            assertThat(alterar(bruno, reserva, "14:00", "16:00").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(alterar(rita, reserva, "14:00", "16:00").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("reserva cancelada → 422")
        void cancelada() {
            long reserva = reservar(ana, sala, null, "10:00", "12:00");
            assertThat(acao(ana, reserva, "cancelamento").getStatusCode()).isEqualTo(HttpStatus.OK);

            var resposta = alterar(ana, reserva, "14:00", "16:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody().get("detail").asText())
                    .isEqualTo("Só é possível alterar o horário de reservas solicitadas ou aprovadas.");
        }

        @Test
        @DisplayName("reserva que já começou → 422")
        void jaComecou() {
            long reserva = idDe(http.postForEntity("/api/reservas", new HttpEntity<>(corpoReserva(sala, null,
                    HA_30_DIAS, "10:00", "12:00"), cabecalhos(ana)), JsonNode.class));

            var resposta = alterar(ana, reserva, "14:00", "16:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody().get("detail").asText())
                    .isEqualTo("O horário só pode ser alterado antes do início da reserva.");
        }

        @Test
        @DisplayName("entrada inválida: 404 inexistente, 422 fim antes do início, 400 minuto quebrado ou sem corpo")
        void entradaInvalida() {
            long reserva = reservar(ana, sala, null, "10:00", "12:00");

            assertThat(alterar(ana, 999_999_999L, "14:00", "16:00").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(alterar(ana, reserva, "16:00", "14:00").getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(alterar(ana, reserva, "14:00", "15:30:30").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(http.exchange("/api/reservas/" + reserva + "/horario", HttpMethod.PUT,
                    new HttpEntity<>(cabecalhos(ana)), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(periodoGravado(reserva)).containsExactly(horario("10:00"), horario("12:00"));
        }
    }

    @Nested
    @DisplayName("Concorrência")
    class Concorrencia {

        @RepeatedTest(value = 5, name = "rodada {currentRepetition} de {totalRepetitions}")
        @DisplayName("alterações simultâneas da mesma reserva: nenhum 500 e reserva e ocupação sempre iguais")
        void alteracoesSimultaneas() throws Exception {
            long reserva = reservar(ana, sala, null, "08:00", "09:00");
            String[][] horarios = {{"10:00", "11:00"}, {"12:00", "13:00"}, {"14:00", "15:00"}, {"16:00", "17:00"},
                    {"18:00", "19:00"}};
            var prontos = new CountDownLatch(horarios.length);
            var largada = new CountDownLatch(1);
            List<Integer> status = new ArrayList<>();

            try (ExecutorService executor = Executors.newFixedThreadPool(horarios.length)) {
                List<Future<ResponseEntity<JsonNode>>> futuros = new ArrayList<>();
                for (String[] h : horarios) {
                    futuros.add(executor.submit(() -> {
                        prontos.countDown();
                        largada.await();
                        return alterar(ana, reserva, h[0], h[1]);
                    }));
                }
                assertThat(prontos.await(10, SECONDS)).isTrue();
                largada.countDown();
                for (var futuro : futuros) {
                    status.add(futuro.get(30, SECONDS).getStatusCode().value());
                }
            }

            assertThat(status).as("aceitas ou recusadas como edição concorrente").containsAnyOf(200)
                    .allMatch(s -> s == 200 || s == 409);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM ocupacao o JOIN reserva r ON r.id = o.reserva_id
                    WHERE r.id = ? AND (o.inicio <> r.inicio OR o.fim <> r.fim)""", Integer.class, reserva))
                    .as("ocupação dessincronizada da reserva").isZero();
        }
    }

    // ---------------------------------------------------------------- utilitários

    String token(String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario(perfil), perfil);
    }

    long professor() {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Carlos Lima");
        corpo.put("restrito", false);
        corpo.put("matricula", UUID.randomUUID().toString().substring(0, 12));
        return idDe(http.postForEntity("/api/professores", new HttpEntity<>(corpo, cabecalhos(adm)), JsonNode.class));
    }

    Map<String, Object> corpoReserva(long recurso, Long professor, LocalDate dia, String inicio, String fim) {
        var corpo = new HashMap<String, Object>();
        corpo.put("salaId", recurso);
        corpo.put("professorId", professor);
        corpo.put("inicio", dia.atTime(LocalTime.parse(inicio)).atZone(BRASILIA).toOffsetDateTime().toString());
        corpo.put("fim", dia.atTime(LocalTime.parse(fim)).atZone(BRASILIA).toOffsetDateTime().toString());
        corpo.put("finalidade", "Aula");
        return corpo;
    }

    long reservar(String token, long recurso, Long professor, String inicio, String fim) {
        return idDe(http.postForEntity("/api/reservas", new HttpEntity<>(corpoReserva(recurso, professor, DIA, inicio,
                fim), cabecalhos(token)), JsonNode.class));
    }

    int reservarStatus(String token, long recurso, String inicio, String fim) {
        return http.postForEntity("/api/reservas", new HttpEntity<>(corpoReserva(recurso, null, DIA, inicio, fim),
                cabecalhos(token)), JsonNode.class).getStatusCode().value();
    }

    ResponseEntity<JsonNode> alterar(String token, long reserva, String inicio, String fim) {
        Map<String, Object> corpo = Map.of("inicio", texto(inicio), "fim", texto(fim));
        return http.exchange("/api/reservas/" + reserva + "/horario", HttpMethod.PUT,
                new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    ResponseEntity<JsonNode> acao(String token, long reserva, String caminho) {
        return http.postForEntity("/api/reservas/" + reserva + "/" + caminho, new HttpEntity<>(Map.of(),
                cabecalhos(token)), JsonNode.class);
    }

    List<OffsetDateTime> periodoGravado(long reserva) {
        return jdbc.queryForObject("SELECT inicio, fim FROM reserva WHERE id = ?", (rs, i) -> List.of(
                rs.getObject("inicio", OffsetDateTime.class), rs.getObject("fim", OffsetDateTime.class)), reserva)
                .stream().map(t -> t.toInstant().atOffset(java.time.ZoneOffset.UTC)).toList();
    }

    int contarOcupacoesNoPeriodo(long reserva, String inicio, String fim) {
        return jdbc.queryForObject("SELECT count(*) FROM ocupacao WHERE reserva_id = ? AND inicio = ? AND fim = ?",
                Integer.class, reserva, horario(inicio), horario(fim));
    }

    List<String> acoesAuditadas(long reserva) {
        return jdbc.queryForList("""
                SELECT acao || ' por ' || usuario_email || coalesce(': ' || motivo, '') FROM registro_auditoria
                WHERE entidade = 'RESERVA' AND entidade_id = ? ORDER BY id""", String.class, reserva);
    }

    static long idDe(ResponseEntity<JsonNode> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    static OffsetDateTime instante(ResponseEntity<JsonNode> resposta, String campo) {
        return OffsetDateTime.parse(resposta.getBody().get(campo).asText()).toInstant().atOffset(java.time.ZoneOffset.UTC);
    }

    /** Horário do dia de teste, normalizado em UTC para comparar com o que o banco e a API devolvem. */
    static OffsetDateTime horario(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(BRASILIA).toInstant().atOffset(java.time.ZoneOffset.UTC);
    }

    static String texto(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(BRASILIA).toOffsetDateTime().toString();
    }

    static HttpHeaders cabecalhos(String token) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }
}
