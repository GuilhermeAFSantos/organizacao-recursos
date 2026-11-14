package br.senac.qs.recursos.web.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Listagem de reservas (apoio à interface, RF13): "minhas reservas" para o SOLICITANTE e a fila de
 * aprovação para o RESPONSAVEL. Usa dias só desta classe, para os filtros por período isolarem os dados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Listagem de reservas via API")
class ListagemReservasIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.now(BRASILIA).plusDays(60);

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

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        emailAna = cenario.criarUsuario("SOLICITANTE");
        ana = GeradorTokenTeste.token(jwtEncoder, emailAna, "SOLICITANTE");
        bruno = token("SOLICITANTE");
        rita = token("RESPONSAVEL");
        adm = token("ADMINISTRADOR");
    }

    @Test
    @DisplayName("SOLICITANTE vê só as próprias reservas, em ordem de início, com os recursos pelo nome")
    void minhasReservas() {
        long sala = cenario.criarSala(false);
        long tarde = reservar(ana, DIA, sala, "14:00", "15:00");
        long manha = reservar(ana, DIA, sala, "08:00", "09:00");
        long doBruno = reservar(bruno, DIA, sala, "10:00", "11:00");

        var resposta = listar(ana, "");

        var ids = ids(resposta);
        assertThat(ids).contains(manha, tarde).doesNotContain(doBruno);
        assertThat(ids.indexOf(manha)).isLessThan(ids.indexOf(tarde));
        var item = item(resposta, manha);
        assertThat(item.get("solicitante").asText()).isEqualTo(emailAna);
        assertThat(item.get("status").asText()).isEqualTo("APROVADA");
        assertThat(item.get("recursos")).singleElement().satisfies(r -> {
            assertThat(r.get("id").asLong()).isEqualTo(sala);
            assertThat(r.get("tipo").asText()).isEqualTo("SALA");
            assertThat(r.get("nome").asText()).startsWith("Sala ");
        });
    }

    @Test
    @DisplayName("RESPONSAVEL vê a fila de aprovação: só SOLICITADAS, de qualquer solicitante")
    void filaDeAprovacao() {
        long laboratorio = cenario.criarSala(true);
        long daAna = reservar(ana, DIA.plusDays(1), laboratorio, "08:00", "09:00");
        long doBruno = reservar(bruno, DIA.plusDays(1), laboratorio, "10:00", "11:00");
        long aprovada = reservar(ana, DIA.plusDays(1), cenario.criarSala(false), "08:00", "09:00");

        var resposta = listar(rita, "?status=SOLICITADA&de=" + DIA.plusDays(1) + "&ate=" + DIA.plusDays(1));

        assertThat(ids(resposta)).containsExactly(daAna, doBruno).doesNotContain(aprovada);
    }

    @Test
    @DisplayName("período filtra pelo dia (horário de Brasília); o ADMINISTRADOR vê todos")
    void periodo() {
        long sala = cenario.criarSala(false);
        long noDia = reservar(ana, DIA.plusDays(2), sala, "10:00", "11:00");
        long noOutroDia = reservar(bruno, DIA.plusDays(3), sala, "10:00", "11:00");

        var resposta = listar(adm, "?de=" + DIA.plusDays(2) + "&ate=" + DIA.plusDays(2));

        assertThat(ids(resposta)).contains(noDia).doesNotContain(noOutroDia);
    }

    @Test
    @DisplayName("reserva rejeitada mostra o motivo")
    void motivo() {
        long reserva = reservar(ana, DIA.plusDays(4), cenario.criarSala(true), "08:00", "09:00");
        assertThat(http.postForEntity("/api/reservas/" + reserva + "/rejeicao", new HttpEntity<>(
                Map.of("motivo", "Sala em reforma"), cabecalhos(rita)), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(item(listar(ana, ""), reserva).get("motivo").asText()).isEqualTo("Sala em reforma");
    }

    @Test
    @DisplayName("sem token: 401")
    void semToken() {
        assertThat(http.getForEntity("/api/reservas", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest(name = "filtros: [{0}]")
    @ValueSource(strings = {"?status=PENDENTE", "?de=2030-01-10&ate=2030-01-09", "?tamanho=101", "?de=10/01/2030"})
    @DisplayName("filtros inválidos: 400")
    void filtrosInvalidos(String filtros) {
        assertThat(listar(rita, filtros).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------------------------------------------------------------- utilitários

    String token(String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario(perfil), perfil);
    }

    long reservar(String token, LocalDate dia, long sala, String inicio, String fim) {
        var corpo = new HashMap<String, Object>();
        corpo.put("salaId", sala);
        corpo.put("inicio", texto(dia, inicio));
        corpo.put("fim", texto(dia, fim));
        corpo.put("finalidade", "Aula");
        var resposta = http.postForEntity("/api/reservas", new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    ResponseEntity<JsonNode> listar(String token, String filtros) {
        return http.exchange("/api/reservas" + filtros, HttpMethod.GET, new HttpEntity<>(cabecalhos(token)),
                JsonNode.class);
    }

    static List<Long> ids(ResponseEntity<JsonNode> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Long> ids = new ArrayList<>();
        resposta.getBody().get("itens").forEach(i -> ids.add(i.get("id").asLong()));
        return ids;
    }

    static JsonNode item(ResponseEntity<JsonNode> resposta, long id) {
        for (var i : resposta.getBody().get("itens")) {
            if (i.get("id").asLong() == id) {
                return i;
            }
        }
        throw new AssertionError("reserva " + id + " não listada");
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
