package br.senac.qs.recursos.web.notificacao;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.GeradorTokenTeste;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Notificação ao solicitante (RF11). O WireMock faz o papel do serviço externo de notificação:
 * conferimos o que chegou nele e como a aplicação reage quando ele falha ou demora.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Notificação ao solicitante (serviço externo simulado com WireMock)")
class NotificacaoIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.now(BRASILIA).plusDays(30);
    static final String CAMINHO = "/notificacoes";
    static final ObjectMapper JSON = new ObjectMapper();

    static final WireMockServer servicoExterno = new WireMockServer(options().dynamicPort());

    static {
        servicoExterno.start();
    }

    @DynamicPropertySource
    static void apontarParaOWireMock(DynamicPropertyRegistry propriedades) {
        propriedades.add("app.notificacao.url-base", servicoExterno::baseUrl);
        propriedades.add("app.notificacao.timeout", () -> "PT0.5S");
    }

    @AfterAll
    static void desligar() {
        servicoExterno.stop();
    }

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

    @BeforeEach
    void prepararCenario() {
        servicoExterno.resetAll();
        servicoExterno.stubFor(post(CAMINHO).willReturn(aResponse().withStatus(202)));
        cenario = new CenarioBanco(jdbc);
        emailAna = cenario.criarUsuario("SOLICITANTE");
        ana = GeradorTokenTeste.token(jwtEncoder, emailAna, "SOLICITANTE");
        rita = GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario("RESPONSAVEL"), "RESPONSAVEL");
    }

    @Test
    @DisplayName("reserva aprovada automaticamente: uma única notificação, em JSON, para quem reservou")
    void criacao() {
        long reserva = idDe(reservar(cenario.criarSala(false), "10:00", "12:00"));

        var recebidas = notificacoesDa(reserva);

        assertThat(recebidas).singleElement().satisfies(n -> {
            assertThat(n.get("evento").asText()).isEqualTo("RESERVA_APROVADA");
            assertThat(n.get("destinatario").asText()).isEqualTo(emailAna);
            assertThat(n.get("mensagem").asText()).contains("foi aprovada automaticamente");
            assertThat(n.get("ocorridoEm").asText()).isNotBlank();
        });
        servicoExterno.verify(postRequestedFor(urlEqualTo(CAMINHO))
                .withHeader("Content-Type", equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withRequestBody(matchingJsonPath("$.reservaId", equalTo(String.valueOf(reserva)))));
    }

    @Test
    @DisplayName("laboratório: avisa que aguarda aprovação e, depois, que foi aprovada")
    void aprovacao() {
        long reserva = idDe(reservar(cenario.criarSala(true), "10:00", "12:00"));
        assertThat(http.postForEntity("/api/reservas/" + reserva + "/aprovacao", new HttpEntity<>(Map.of(),
                cabecalhos(rita)), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(notificacoesDa(reserva)).extracting(n -> n.get("evento").asText())
                .containsExactly("RESERVA_SOLICITADA", "RESERVA_APROVADA");
    }

    @Test
    @DisplayName("alteração de horário gera o evento RESERVA_HORARIO_ALTERADO")
    void alteracao() {
        long reserva = idDe(reservar(cenario.criarSala(false), "10:00", "12:00"));

        var alterada = http.exchange("/api/reservas/" + reserva + "/horario", HttpMethod.PUT,
                new HttpEntity<>(Map.of("inicio", texto("14:00"), "fim", texto("16:00")), cabecalhos(ana)),
                JsonNode.class);

        assertThat(alterada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notificacoesDa(reserva)).last().satisfies(n ->
                assertThat(n.get("evento").asText()).isEqualTo("RESERVA_HORARIO_ALTERADO"));
    }

    @Test
    @DisplayName("operação recusada (409) não notifica: nada foi gravado")
    void recusaNaoNotifica() {
        long sala = cenario.criarSala(false);
        idDe(reservar(sala, "10:00", "12:00"));
        int antes = servicoExterno.getAllServeEvents().size();

        assertThat(reservar(sala, "11:00", "13:00").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(servicoExterno.getAllServeEvents()).hasSize(antes);
    }

    @Test
    @DisplayName("serviço externo com erro 500: a reserva continua criada")
    void servicoComErro() {
        servicoExterno.stubFor(post(CAMINHO).willReturn(aResponse().withStatus(500)));
        long sala = cenario.criarSala(false);

        var resposta = reservar(sala, "10:00", "12:00");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cenario.contarReservas(sala)).isEqualTo(1);
        assertThat(notificacoesDa(idDe(resposta))).as("a tentativa foi feita").hasSize(1);
    }

    @Test
    @DisplayName("serviço externo lento (3 s): o tempo limite corta a espera e a reserva continua criada")
    void servicoLento() {
        servicoExterno.stubFor(post(CAMINHO).willReturn(aResponse().withStatus(202).withFixedDelay(3_000)));
        long sala = cenario.criarSala(false);

        long comeco = System.nanoTime();
        var resposta = reservar(sala, "10:00", "12:00");
        var duracao = Duration.ofNanos(System.nanoTime() - comeco);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(cenario.contarReservas(sala)).isEqualTo(1);
        assertThat(duracao).as("bem menos que os 3 s do serviço").isLessThan(Duration.ofMillis(2_500));
    }

    // ---------------------------------------------------------------- utilitários

    /** Notificações da reserva em ordem cronológica (pelo instante da mudança). */
    List<JsonNode> notificacoesDa(long reserva) {
        return servicoExterno.findAll(postRequestedFor(urlEqualTo(CAMINHO))).stream()
                .map(requisicao -> lerJson(requisicao.getBodyAsString()))
                .filter(n -> n.get("reservaId").asLong() == reserva)
                .sorted(Comparator.comparing(n -> Instant.parse(n.get("ocorridoEm").asText())))
                .toList();
    }

    static JsonNode lerJson(String corpo) {
        try {
            return JSON.readTree(corpo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corpo da notificação não é JSON: " + corpo, e);
        }
    }

    ResponseEntity<JsonNode> reservar(long sala, String inicio, String fim) {
        Map<String, Object> corpo = Map.of("salaId", sala, "inicio", texto(inicio), "fim", texto(fim),
                "finalidade", "Aula");
        return http.postForEntity("/api/reservas", new HttpEntity<>(corpo, cabecalhos(ana)), JsonNode.class);
    }

    static long idDe(ResponseEntity<JsonNode> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
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
