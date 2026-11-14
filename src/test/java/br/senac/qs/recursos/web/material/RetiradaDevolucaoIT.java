package br.senac.qs.recursos.web.material;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/** Retirada e devolução de materiais via API (RF09). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Retirada e devolução de materiais via API")
class RetiradaDevolucaoIT {

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
    String emailRita;
    String rita;
    String adm;
    long projetor;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        emailAna = cenario.criarUsuario("SOLICITANTE");
        ana = GeradorTokenTeste.token(jwtEncoder, emailAna, "SOLICITANTE");
        emailRita = cenario.criarUsuario("RESPONSAVEL");
        rita = GeradorTokenTeste.token(jwtEncoder, emailRita, "RESPONSAVEL");
        adm = GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario("ADMINISTRADOR"), "ADMINISTRADOR");
        projetor = material();
    }

    @Nested
    @DisplayName("Fluxo")
    class Fluxo {

        @Test
        @DisplayName("retira, aparece nas pendências com quem reservou, devolve e o estado do material é atualizado")
        void retiraEDevolve() {
            long reserva = reservar(DIA, projetor);

            var retirada = retirar(rita, reserva, projetor);
            var pendentesComItem = pendencias();
            var devolucao = devolver(rita, reserva, projetor, "REGULAR", "Riscos na lente");

            assertThat(retirada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(retirada.getBody().get("tipo").asText()).isEqualTo("RETIRADA");
            assertThat(retirada.getBody().get("estadoConservacao").asText()).isEqualTo("BOM");
            assertThat(retirada.getBody().get("registradoPor").asText()).isEqualTo(emailRita);
            assertThat(pendentesComItem).filteredOn(p -> p.get("materialId").asLong() == projetor).singleElement()
                    .satisfies(p -> {
                        assertThat(p.get("reservaId").asLong()).isEqualTo(reserva);
                        assertThat(p.get("solicitante").asText()).isEqualTo(emailAna);
                        assertThat(p.get("atrasada").asBoolean()).isFalse();
                    });
            assertThat(devolucao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(devolucao.getBody().get("observacao").asText()).isEqualTo("Riscos na lente");
            assertThat(devolucao.getBody().get("atrasada").asBoolean()).isFalse();
            assertThat(pendencias()).noneMatch(p -> p.get("materialId").asLong() == projetor);
            assertThat(get(ana, "/api/recursos/" + projetor).getBody().get("estadoConservacao").asText())
                    .isEqualTo("REGULAR");
            assertThat(get(rita, "/api/materiais/" + projetor + "/movimentacoes").getBody().findValuesAsText("tipo"))
                    .containsExactly("RETIRADA", "DEVOLUCAO");
        }

        @Test
        @DisplayName("devolvido danificado: exige descrever o dano e o material deixa de ser reservável")
        void danificado() {
            long reserva = reservar(DIA, projetor);
            retirar(rita, reserva, projetor);

            var semObservacao = devolver(rita, reserva, projetor, "DANIFICADO", null);
            var comObservacao = devolver(rita, reserva, projetor, "DANIFICADO", "Lâmpada queimada");
            var novaReserva = post(ana, "/api/reservas", corpoReserva(DIA, "14:00", "16:00", projetor));

            assertThat(semObservacao.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(semObservacao.getBody().get("detail").asText()).isEqualTo("Descreva o dano na observação.");
            assertThat(comObservacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(novaReserva.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(novaReserva.getBody().get("detail").asText()).contains("está danificado");
        }

        @Test
        @DisplayName("retirado de reserva que já terminou aparece como atrasado")
        void pendenciaAtrasada() {
            long reserva = cenario.criarReservaAprovadaNoPassado(emailAna, projetor, HA_30_DIAS);
            retirar(rita, reserva, projetor);

            assertThat(pendencias()).filteredOn(p -> p.get("materialId").asLong() == projetor).singleElement()
                    .satisfies(p -> assertThat(p.get("atrasada").asBoolean()).isTrue());
        }
    }

    @Nested
    @DisplayName("Recusas")
    class Recusas {

        @Test
        @DisplayName("SOLICITANTE não registra (403); o ADMINISTRADOR registra")
        void permissao() {
            long reserva = reservar(DIA, projetor);

            assertThat(retirar(ana, reserva, projetor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get(ana, "/api/movimentacoes/pendentes").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(retirar(adm, reserva, projetor).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("material de outra reserva: 422; reserva ou material inexistentes: 404")
        void foraDaReserva() {
            long reserva = reservar(DIA, projetor);
            long outro = material();

            assertThat(retirar(rita, reserva, outro).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(retirar(rita, 999_999_999L, projetor).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(retirar(rita, reserva, 999_999_999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("retirar duas vezes, ou com o item ainda em outra reserva: 422")
        void indisponivel() {
            long primeira = reservar(DIA, projetor);
            long segunda = idDe(post(ana, "/api/reservas", corpoReserva(DIA, "14:00", "16:00", projetor)));
            retirar(rita, primeira, projetor);

            var deNovo = retirar(rita, primeira, projetor);
            var naOutra = retirar(rita, segunda, projetor);

            assertThat(deNovo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(deNovo.getBody().get("detail").asText()).contains("já foi retirado");
            assertThat(naOutra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(naOutra.getBody().get("detail").asText()).contains("ainda não foi devolvido de outra reserva");
        }

        @Test
        @DisplayName("reserva aguardando aprovação: 422; devolver sem ter retirado: 422")
        void estado() {
            long laboratorio = cenario.criarSala(true);
            long aguardando = idDe(post(ana, "/api/reservas", Map.of("salaId", laboratorio, "materiaisIds",
                    List.of(projetor), "inicio", texto(DIA, "10:00"), "fim", texto(DIA, "12:00"), "finalidade", "Aula")));

            assertThat(retirar(rita, aguardando, projetor).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(devolver(rita, aguardando, projetor, "BOM", null).getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("corpo inválido: estado fora da lista ou observação longa demais → 400")
        void corpoInvalido() {
            long reserva = reservar(DIA, projetor);
            retirar(rita, reserva, projetor);

            assertThat(devolver(rita, reserva, projetor, "QUEBRADO", null).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(devolver(rita, reserva, projetor, "BOM", "x".repeat(501)).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(devolver(rita, reserva, projetor, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Integridade")
    class Integridade {

        @Test
        @DisplayName("o histórico de movimentações não pode ser alterado nem apagado, nem por SQL direto")
        void historicoImutavel() {
            long reserva = reservar(DIA, projetor);
            long id = retirar(rita, reserva, projetor).getBody().get("id").asLong();

            assertThatThrownBy(() -> jdbc.update("UPDATE movimentacao_material SET estado_conservacao = 'BOM' "
                    + "WHERE id = ?", id)).isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("somente inserção");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM movimentacao_material WHERE id = ?", id))
                    .isInstanceOf(DataAccessException.class);
        }

        @RepeatedTest(value = 5, name = "rodada {currentRepetition} de {totalRepetitions}")
        @DisplayName("5 retiradas simultâneas do mesmo item: exatamente 1 aceita")
        void retiradasSimultaneas() throws Exception {
            long reserva = reservar(DIA, projetor);
            int n = 5;
            var prontos = new CountDownLatch(n);
            var largada = new CountDownLatch(1);
            List<Integer> status = new ArrayList<>();

            try (ExecutorService executor = Executors.newFixedThreadPool(n)) {
                List<Future<ResponseEntity<JsonNode>>> futuros = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    futuros.add(executor.submit(() -> {
                        prontos.countDown();
                        largada.await();
                        return retirar(rita, reserva, projetor);
                    }));
                }
                assertThat(prontos.await(10, SECONDS)).isTrue();
                largada.countDown();
                for (var futuro : futuros) {
                    status.add(futuro.get(30, SECONDS).getStatusCode().value());
                }
            }

            assertThat(status.stream().collect(groupingBy(s -> s, counting())))
                    .containsOnly(entry(201, 1L), entry(422, n - 1L));
        }
    }

    // ---------------------------------------------------------------- utilitários

    long material() {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Projetor");
        corpo.put("restrito", false);
        corpo.put("patrimonio", UUID.randomUUID().toString().substring(0, 12));
        corpo.put("tipoMaterial", "Projetor");
        corpo.put("estadoConservacao", "BOM");
        return idDe(post(adm, "/api/materiais", corpo));
    }

    long reservar(LocalDate dia, long material) {
        return idDe(post(ana, "/api/reservas", corpoReserva(dia, "10:00", "12:00", material)));
    }

    Map<String, Object> corpoReserva(LocalDate dia, String inicio, String fim, long material) {
        return Map.of("salaId", cenario.criarSala(false), "materiaisIds", List.of(material),
                "inicio", texto(dia, inicio), "fim", texto(dia, fim), "finalidade", "Aula");
    }

    ResponseEntity<JsonNode> retirar(String token, long reserva, long material) {
        return post(token, "/api/reservas/" + reserva + "/materiais/" + material + "/retirada", Map.of());
    }

    ResponseEntity<JsonNode> devolver(String token, long reserva, long material, String estado, String observacao) {
        var corpo = new HashMap<String, Object>();
        corpo.put("estadoConservacao", estado);
        corpo.put("observacao", observacao);
        return post(token, "/api/reservas/" + reserva + "/materiais/" + material + "/devolucao", corpo);
    }

    List<JsonNode> pendencias() {
        var resposta = get(rita, "/api/movimentacoes/pendentes");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<JsonNode> itens = new ArrayList<>();
        resposta.getBody().forEach(itens::add);
        return itens;
    }

    ResponseEntity<JsonNode> post(String token, String caminho, Map<String, ?> corpo) {
        return http.postForEntity(caminho, new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    ResponseEntity<JsonNode> get(String token, String caminho) {
        return http.exchange(caminho, HttpMethod.GET, new HttpEntity<>(cabecalhos(token)), JsonNode.class);
    }

    static long idDe(ResponseEntity<JsonNode> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
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
