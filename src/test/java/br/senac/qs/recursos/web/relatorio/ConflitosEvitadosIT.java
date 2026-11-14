package br.senac.qs.recursos.web.relatorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.GeradorTokenTeste;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Conflitos evitados (RF12): cada reserva recusada por sobreposição é registrada, mesmo com a
 * transação da reserva desfeita, para entrar no relatório.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Registro de conflitos evitados")
class ConflitosEvitadosIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.now(BRASILIA).plusDays(30);

    @Autowired
    TestRestTemplate http;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    String ana;
    String bruno;
    long idBruno;
    long sala;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        ana = token(cenario.criarUsuario("SOLICITANTE"), "SOLICITANTE");
        String emailBruno = cenario.criarUsuario("SOLICITANTE");
        bruno = token(emailBruno, "SOLICITANTE");
        idBruno = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, emailBruno);
        sala = cenario.criarSala(false);
    }

    @Test
    @DisplayName("reserva recusada por sobreposição: registra recurso, quem tentou, horário pedido e origem")
    void criacaoRecusada() {
        reservar(ana, sala, null, "10:00", "12:00");

        assertThat(reservar(bruno, sala, null, "11:00", "13:00").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(conflitosDo(sala)).singleElement().satisfies(c -> {
            assertThat(c.get("solicitante_id")).isEqualTo(idBruno);
            assertThat(c.get("origem")).isEqualTo("APLICACAO");
            assertThat(utc(c.get("inicio"))).isEqualTo(horario("11:00"));
            assertThat(utc(c.get("fim"))).isEqualTo(horario("13:00"));
        });
    }

    @Test
    @DisplayName("reserva aceita, ou recusada por outro motivo (validação), não é conflito")
    void semConflito() {
        reservar(ana, sala, null, "10:00", "12:00");
        reservar(bruno, sala, null, "12:00", "13:00");
        reservar(bruno, sala, null, "14:00", "14:00");

        assertThat(conflitosDo(sala)).isEmpty();
    }

    @Test
    @DisplayName("recusa por manutenção também é conflito evitado (MN-02)")
    void manutencao() {
        String adm = token(cenario.criarUsuario("ADMINISTRADOR"), "ADMINISTRADOR");
        var bloqueio = http.postForEntity("/api/bloqueios", new HttpEntity<>(Map.of("recursoId", sala, "tipo",
                "MANUTENCAO", "motivo", "Pintura", "inicio", texto("10:00"), "fim", texto("12:00")), cabecalhos(adm)),
                JsonNode.class);
        assertThat(bloqueio.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(reservar(bruno, sala, null, "10:30", "11:30").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(conflitosDo(sala)).hasSize(1);
    }

    @Test
    @DisplayName("com vários recursos, registra o que conflitou (o professor), não a sala livre")
    void recursoQueConflitou() {
        long professor = professor();
        reservar(ana, cenario.criarSala(false), professor, "10:00", "12:00");
        long salaLivre = cenario.criarSala(false);

        assertThat(reservar(bruno, salaLivre, professor, "11:00", "12:00").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(conflitosDo(professor)).hasSize(1);
        assertThat(conflitosDo(salaLivre)).isEmpty();
    }

    @Test
    @DisplayName("alteração de horário recusada também é registrada")
    void alteracaoRecusada() {
        reservar(ana, sala, null, "10:00", "12:00");
        long reserva = reservar(bruno, sala, null, "14:00", "15:00").getBody().get("id").asLong();

        var resposta = http.exchange("/api/reservas/" + reserva + "/horario", HttpMethod.PUT,
                new HttpEntity<>(Map.of("inicio", texto("11:00"), "fim", texto("12:00")), cabecalhos(bruno)),
                JsonNode.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflitosDo(sala)).singleElement()
                .satisfies(c -> assertThat(c.get("solicitante_id")).isEqualTo(idBruno));
    }

    // ---------------------------------------------------------------- utilitários

    String token(String email, String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, email, perfil);
    }

    long professor() {
        String adm = token(cenario.criarUsuario("ADMINISTRADOR"), "ADMINISTRADOR");
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Carlos Lima");
        corpo.put("matricula", UUID.randomUUID().toString().substring(0, 12));
        var resposta = http.postForEntity("/api/professores", new HttpEntity<>(corpo, cabecalhos(adm)), JsonNode.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    ResponseEntity<JsonNode> reservar(String token, long recurso, Long professor, String inicio, String fim) {
        var corpo = new HashMap<String, Object>();
        corpo.put("salaId", recurso);
        corpo.put("professorId", professor);
        corpo.put("inicio", texto(inicio));
        corpo.put("fim", texto(fim));
        corpo.put("finalidade", "Aula");
        return http.postForEntity("/api/reservas", new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    List<Map<String, Object>> conflitosDo(long recurso) {
        return jdbc.queryForList("SELECT * FROM conflito_evitado WHERE recurso_id = ? ORDER BY id", recurso);
    }

    static OffsetDateTime utc(Object valor) {
        return ((java.sql.Timestamp) valor).toInstant().atOffset(ZoneOffset.UTC);
    }

    static OffsetDateTime horario(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(BRASILIA).toInstant().atOffset(ZoneOffset.UTC);
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
