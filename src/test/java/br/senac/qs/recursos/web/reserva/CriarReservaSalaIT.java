package br.senac.qs.recursos.web.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.GeradorTokenTeste;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * Teste de API em CAIXA-PRETA da criação de reserva de sala: servidor HTTP real, JWT real e
 * PostgreSQL real (Testcontainers) com a constraint de exclusão ativa. Nada é simulado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("POST /api/reservas: criação de reserva de sala")
class CriarReservaSalaIT {

    static final String URL = "/api/reservas";
    static final String DIA = "2026-12-01T";
    static final String BRASILIA = "-03:00";

    @Autowired
    TestRestTemplate http;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    String emailSolicitante;
    String tokenSolicitante;
    long sala;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        emailSolicitante = cenario.criarUsuario("SOLICITANTE");
        tokenSolicitante = token(emailSolicitante, "SOLICITANTE");
        sala = cenario.criarSala();
    }

    @Test
    @DisplayName("cria a reserva (201), devolve o período em UTC e ocupa o horário da sala")
    void criaReserva() {
        var resposta = reservar(tokenSolicitante, corpo(sala, "08:00:00", "10:00:00"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode reserva = resposta.getBody();
        assertThat(reserva.get("id").asLong()).isPositive();
        assertThat(reserva.get("salaId").asLong()).isEqualTo(sala);
        assertThat(reserva.get("inicio").asText()).isEqualTo("2026-12-01T11:00:00Z");
        assertThat(reserva.get("fim").asText()).isEqualTo("2026-12-01T13:00:00Z");
        assertThat(reserva.get("finalidade").asText()).isEqualTo("Aula de Qualidade de Software");
        assertThat(cenario.contarOcupacoesBloqueantes(sala)).isEqualTo(1);
    }

    @Test
    @DisplayName("AP-01/RC08: sala não restrita é aprovada automaticamente e as 2 mudanças vão para a auditoria")
    void aprovacaoAutomaticaAuditada() {
        var resposta = reservar(tokenSolicitante, corpo(sala, "08:00:00", "10:00:00"));

        assertThat(resposta.getBody().get("status").asText()).isEqualTo("APROVADA");
        assertThat(cenario.auditoriaDaReserva(resposta.getBody().get("id").asLong())).containsExactly(
                "—→SOLICITADA por " + emailSolicitante,
                "SOLICITADA→APROVADA por sistema");
    }

    @Test
    @DisplayName("AP-02/RC08: sala restrita fica SOLICITADA, já ocupa o horário e a criação é auditada")
    void salaRestritaAguardaAprovacao() {
        long restrita = cenario.criarSala(true);

        var resposta = reservar(tokenSolicitante, corpo(restrita, "08:00:00", "10:00:00"));

        assertThat(resposta.getBody().get("status").asText()).isEqualTo("SOLICITADA");
        assertThat(cenario.contarOcupacoesBloqueantes(restrita)).isEqualTo(1);
        assertThat(cenario.auditoriaDaReserva(resposta.getBody().get("id").asLong()))
                .containsExactly("—→SOLICITADA por " + emailSolicitante);
    }

    @Nested
    @DisplayName("RC01: término posterior ao início")
    class TerminoPosteriorAoInicio {

        @ParameterizedTest(name = "{2} → 422")
        @CsvSource(delimiter = '|', textBlock = """
            2026-12-01T10:00:00-03:00 | 2026-12-01T10:00:00-03:00 | término igual ao início
            2026-12-01T10:00:00-03:00 | 2026-12-01T09:00:00-03:00 | término antes do início
            2026-12-01T10:00:00-03:00 | 2026-12-01T13:00:00Z      | duração zero com fusos diferentes
            """)
        void rejeitaPeriodoInvalido(String inicio, String fim, String caso) {
            var resposta = reservar(tokenSolicitante, Map.of(
                    "salaId", sala, "inicio", inicio, "fim", fim, "finalidade", "Aula"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody().get("title").asText()).isEqualTo("Regra de negócio violada");
            assertThat(resposta.getBody().get("detail").asText())
                    .isEqualTo("O término da reserva deve ser posterior ao início.");
            assertThat(cenario.contarReservas(sala)).isZero();
        }
    }

    @Nested
    @DisplayName("RC02: sem sobreposição no mesmo recurso (já existe reserva das 08:00 às 10:00)")
    class SemSobreposicao {

        @BeforeEach
        void reservaExistente() {
            assertThat(reservar(tokenSolicitante, corpo(sala, "08:00:00", "10:00:00")).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        @ParameterizedTest(name = "nova reserva [{0}–{1}): {2} → {3}")
        @CsvSource(delimiter = '|', textBlock = """
            10:00:00 | 12:00:00 | começa exatamente quando a outra termina | 201
            06:00:00 | 08:00:00 | termina exatamente quando a outra começa | 201
            09:00:00 | 11:00:00 | sobreposição parcial                     | 409
            07:00:00 | 08:01:00 | cruza 1 minuto                           | 409
            08:00:00 | 10:00:00 | mesmo horário                            | 409
            """)
        void verificaSobreposicao(String inicio, String fim, String caso, int statusEsperado) {
            var resposta = reservar(tokenSolicitante, corpo(sala, inicio, fim));

            assertThat(resposta.getStatusCode().value()).isEqualTo(statusEsperado);
            if (statusEsperado == 409) {
                assertThat(resposta.getBody().get("title").asText()).isEqualTo("Conflito de horário");
                assertThat(resposta.getBody().get("detail").asText()).contains("já está ocupada");
                assertThat(cenario.contarReservas(sala)).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("outra sala no mesmo horário é aceita (a regra vale por recurso)")
        void outraSalaMesmoHorario() {
            long outraSala = cenario.criarSala();

            assertThat(reservar(tokenSolicitante, corpo(outraSala, "08:00:00", "10:00:00")).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }
    }

    @Nested
    @DisplayName("Entrada inválida e autorização")
    class EntradaEAutorizacao {

        @Test
        @DisplayName("sala inexistente → 404")
        void salaInexistente() {
            var resposta = reservar(tokenSolicitante, corpo(999_999_999L, "08:00:00", "10:00:00"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(resposta.getBody().get("detail").asText()).isEqualTo("Sala 999999999 não encontrada.");
        }

        @Test
        @DisplayName("campos obrigatórios ausentes → 400 listando cada campo")
        void camposObrigatorios() {
            var resposta = reservar(tokenSolicitante, Map.of());

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody().get("erros").findValuesAsText("campo"))
                    .containsExactlyInAnyOrder("finalidade", "fim", "inicio", "salaId");
        }

        @Test
        @DisplayName("horário sem fuso → 400 (evita ambiguidade de fuso)")
        void horarioSemFuso() {
            var resposta = reservar(tokenSolicitante, Map.of(
                    "salaId", sala, "inicio", "2026-12-01T08:00:00", "fim", "2026-12-01T10:00:00", "finalidade", "Aula"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(cenario.contarReservas(sala)).isZero();
        }

        @Test
        @DisplayName("Q3: horário com segundos → 400 apontando o campo")
        void horarioComSegundos() {
            var resposta = reservar(tokenSolicitante, Map.of(
                    "salaId", sala, "inicio", "2026-12-01T08:00:30-03:00", "fim", "2026-12-01T10:00:00-03:00",
                    "finalidade", "Aula"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody().get("erros").findValuesAsText("campo")).containsExactly("inicio");
            assertThat(cenario.contarReservas(sala)).isZero();
        }

        @Test
        @DisplayName("cliente não pode definir o status na criação (mass assignment) → 400")
        void statusNaoPodeSerEnviado() {
            var corpo = new java.util.HashMap<>(corpo(sala, "08:00:00", "10:00:00"));
            corpo.put("status", "APROVADA");

            assertThat(reservar(tokenSolicitante, corpo).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(cenario.contarReservas(sala)).isZero();
        }

        // Pergunta Q2 em aberto (docs/testes/bdd-aprovacao-bloqueio.md): por ora, segue o enunciado,
        // em que só o SOLICITANTE cria reservas.
        @Test
        @DisplayName("perfil RESPONSAVEL não cria reserva → 403")
        void responsavelNaoCria() {
            String tokenResponsavel = token(cenario.criarUsuario("RESPONSAVEL"), "RESPONSAVEL");

            var resposta = reservar(tokenResponsavel, corpo(sala, "08:00:00", "10:00:00"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(cenario.contarReservas(sala)).isZero();
        }
    }

    // ------------------------------------------------------------------ auxiliares

    String token(String email, String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, email, perfil);
    }

    static Map<String, Object> corpo(long salaId, String horaInicio, String horaFim) {
        return Map.of("salaId", salaId,
                      "inicio", DIA + horaInicio + BRASILIA,
                      "fim", DIA + horaFim + BRASILIA,
                      "finalidade", "Aula de Qualidade de Software");
    }

    ResponseEntity<JsonNode> reservar(String token, Map<String, ?> corpo) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(URL, new HttpEntity<>(corpo, cabecalhos), JsonNode.class);
    }
}
