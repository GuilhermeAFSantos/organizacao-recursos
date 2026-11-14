package br.senac.qs.recursos.web.relatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.GeradorTokenTeste;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Relatório de utilização, carga horária e conflitos evitados (RF12).
 *
 * <p>Os totais somam TUDO o que há no período. Por isso cada teste usa um dia só dele, longe das
 * datas usadas pelas outras classes de teste (que ficam a ±30 dias de hoje).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Relatório de utilização")
class RelatorioUtilizacaoIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate FUTURO = LocalDate.now(BRASILIA).plusYears(2);
    static final LocalDate PASSADO = LocalDate.now(BRASILIA).minusYears(2);

    @Autowired
    TestRestTemplate http;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    String ana;
    String bruno;
    String rita;
    String adm;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        ana = token("SOLICITANTE");
        bruno = token("SOLICITANTE");
        rita = token("RESPONSAVEL");
        adm = token("ADMINISTRADOR");
    }

    @Test
    @DisplayName("por recurso e no total: reservas que ocupam, horas, carga do professor e conflitos evitados")
    void utilizacao() {
        LocalDate dia = FUTURO;
        long salaA = cenario.criarSala(false);
        long salaB = cenario.criarSala(false);
        long laboratorio = cenario.criarSala(true);
        long professor = professor();
        reservar(ana, dia, salaA, null, "08:00", "10:00");
        long concluida = reservar(ana, dia, salaA, null, "10:00", "11:30");
        acao(rita, concluida, "inicio-uso");
        acao(rita, concluida, "conclusao");
        // Com professor, a reserva aguarda aprovação (D4); aprovada, passa a contar como utilização.
        acao(rita, reservar(ana, dia, salaB, professor, "13:00", "15:00"), "aprovacao");
        long cancelada = reservar(ana, dia, salaA, null, "14:00", "16:00");
        acao(ana, cancelada, "cancelamento");
        reservar(ana, dia, laboratorio, null, "08:00", "09:00");
        assertThat(tentar(bruno, dia, salaA, "09:00", "10:00").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        var relatorio = relatorio(rita, dia, dia);

        assertThat(relatorio.getStatusCode()).isEqualTo(HttpStatus.OK);
        var corpo = relatorio.getBody();
        var a = linha(corpo, salaA);
        assertThat(a.get("tipo").asText()).isEqualTo("SALA");
        assertThat(a.get("reservas").asInt()).as("aprovada e concluída; cancelada não conta").isEqualTo(2);
        assertThat(a.get("horasReservadas").asDouble()).isEqualTo(3.5);
        assertThat(a.get("conflitosEvitados").asInt()).isEqualTo(1);
        var p = linha(corpo, professor);
        assertThat(p.get("tipo").asText()).isEqualTo("PROFESSOR");
        assertThat(p.get("horasReservadas").asDouble()).as("carga horária do professor").isEqualTo(2.0);
        assertThat(linha(corpo, laboratorio)).as("aguardando aprovação ainda não é utilização").isNull();
        var totais = corpo.get("totais");
        assertThat(totais.get("reservas").asInt()).as("reservas distintas (sala + professor conta uma vez)")
                .isEqualTo(3);
        assertThat(totais.get("horasReservadas").asDouble()).isEqualTo(5.5);
        assertThat(totais.get("conflitosEvitados").asInt()).isEqualTo(1);
        assertThat(totais.get("conflitosPorOrigem").get("APLICACAO").asInt()).isEqualTo(1);
        assertThat(totais.get("conflitosPorOrigem").get("BANCO").asInt()).isZero();
        assertThat(corpo.get("recursos").get(0).get("recursoId").asLong())
                .as("ordenado pelas horas, maior primeiro").isEqualTo(salaA);
    }

    @Test
    @DisplayName("reserva que atravessa a meia-noite conta só as horas dentro do período")
    void recorteNaFronteira() {
        LocalDate dia = FUTURO.plusDays(3);
        long sala = cenario.criarSala(false);
        reservar(ana, dia, sala, null, "23:00", null);

        assertThat(linha(relatorio(rita, dia, dia).getBody(), sala).get("horasReservadas").asDouble()).isEqualTo(1.0);
        assertThat(linha(relatorio(rita, dia.plusDays(1), dia.plusDays(1)).getBody(), sala)
                .get("horasReservadas").asDouble()).isEqualTo(1.0);
        assertThat(linha(relatorio(rita, dia, dia.plusDays(1)).getBody(), sala).get("horasReservadas").asDouble())
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("não comparecimento aparece separado e não soma horas")
    void naoComparecimento() {
        LocalDate dia = PASSADO;
        long sala = cenario.criarSala(false);
        long reserva = reservar(ana, dia, sala, null, "10:00", "12:00");
        acao(rita, reserva, "nao-comparecimento");

        var corpo = relatorio(adm, dia, dia).getBody();

        var l = linha(corpo, sala);
        assertThat(l.get("naoComparecimentos").asInt()).isEqualTo(1);
        assertThat(l.get("reservas").asInt()).isZero();
        assertThat(l.get("horasReservadas").asDouble()).isZero();
        assertThat(corpo.get("totais").get("naoComparecimentos").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("o SOLICITANTE não acessa o relatório (403)")
    void permissao() {
        assertThat(relatorio(ana, FUTURO, FUTURO).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "filtros: [{0}]")
    @ValueSource(strings = {"", "?inicio=2030-01-10", "?inicio=2030-01-10&fim=2030-01-09",
            "?inicio=2030-01-01&fim=2031-01-03", "?inicio=10/01/2030&fim=2030-01-11"})
    @DisplayName("período ausente, invertido, maior que 366 dias ou mal formatado: 400")
    void periodoInvalido(String filtros) {
        var resposta = http.exchange("/api/relatorios/utilizacao" + filtros, HttpMethod.GET,
                new HttpEntity<>(cabecalhos(rita)), JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------------------------------------------------------------- utilitários

    String token(String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario(perfil), perfil);
    }

    long professor() {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Carlos Lima");
        corpo.put("matricula", UUID.randomUUID().toString().substring(0, 12));
        var resposta = http.postForEntity("/api/professores", new HttpEntity<>(corpo, cabecalhos(adm)), JsonNode.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    /** {@code fim} nulo = 01:00 do dia seguinte (para atravessar a meia-noite). */
    ResponseEntity<JsonNode> tentar(String token, LocalDate dia, long sala, Long professor, String inicio,
                                    String fim) {
        var corpo = new HashMap<String, Object>();
        corpo.put("salaId", sala);
        corpo.put("professorId", professor);
        corpo.put("inicio", texto(dia, inicio));
        corpo.put("fim", fim == null ? texto(dia.plusDays(1), "01:00") : texto(dia, fim));
        corpo.put("finalidade", "Aula");
        return http.postForEntity("/api/reservas", new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    ResponseEntity<JsonNode> tentar(String token, LocalDate dia, long sala, String inicio, String fim) {
        return tentar(token, dia, sala, null, inicio, fim);
    }

    long reservar(String token, LocalDate dia, long sala, Long professor, String inicio, String fim) {
        var resposta = tentar(token, dia, sala, professor, inicio, fim);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    void acao(String token, long reserva, String caminho) {
        assertThat(http.postForEntity("/api/reservas/" + reserva + "/" + caminho, new HttpEntity<>(Map.of(),
                cabecalhos(token)), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    ResponseEntity<JsonNode> relatorio(String token, LocalDate inicio, LocalDate fim) {
        return http.exchange("/api/relatorios/utilizacao?inicio=" + inicio + "&fim=" + fim, HttpMethod.GET,
                new HttpEntity<>(cabecalhos(token)), JsonNode.class);
    }

    /** Linha do recurso no relatório, ou nulo se ele não aparece. */
    static JsonNode linha(JsonNode relatorio, long recurso) {
        for (var linha : relatorio.get("recursos")) {
            if (linha.get("recursoId").asLong() == recurso) {
                return linha;
            }
        }
        return null;
    }

    static String texto(LocalDate dia, String hora) {
        return dia.atTime(LocalTime.parse(hora)).atZone(BRASILIA).toOffsetDateTime().toString();
    }

    static HttpHeaders cabecalhos(String token) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }
}
