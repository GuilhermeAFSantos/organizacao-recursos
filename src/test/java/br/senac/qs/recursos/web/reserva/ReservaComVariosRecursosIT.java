package br.senac.qs.recursos.web.reserva;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.function.Supplier;
import java.util.stream.LongStream;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Reserva de sala com professor e materiais (RF04, RF05): tudo ou nada, conflito também na agenda
 * do professor (RF05) e nos materiais, e concorrência sem dupla alocação nem deadlock (RC03).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Reserva com vários recursos via API")
class ReservaComVariosRecursosIT {

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
    String adm;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        ana = token("SOLICITANTE");
        adm = token("ADMINISTRADOR");
    }

    @Nested
    @DisplayName("Criação")
    class Criacao {

        @Test
        @DisplayName("sala, professor e dois materiais: 201 com todos os recursos; com professor, aguarda aprovação (D4)")
        void todosOsRecursos() {
            long sala = cenario.criarSala();
            long professor = professor();
            long projetor = material("BOM");
            long notebook = material("BOM");

            var resposta = reservar(ana, sala, professor, List.of(notebook, projetor), "10:00", "12:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            var corpo = resposta.getBody();
            assertThat(corpo.get("salaId").asLong()).isEqualTo(sala);
            assertThat(corpo.get("professorId").asLong()).isEqualTo(professor);
            assertThat(ids(corpo.get("materiaisIds"))).containsExactly(Math.min(projetor, notebook),
                    Math.max(projetor, notebook));
            assertThat(corpo.get("status").asText()).isEqualTo("SOLICITADA");
            assertThat(contarOcupacoes(corpo.get("id").asLong())).isEqualTo(4);
        }

        @Test
        @DisplayName("sala comum + professor: a reserva aguarda aprovação, porque todo professor é restrito (D4)")
        void professorRestrito() {
            var resposta = reservar(ana, cenario.criarSala(), professor(), List.of(), "10:00", "12:00");

            assertThat(resposta.getBody().get("status").asText()).isEqualTo("SOLICITADA");
        }

        @Test
        @DisplayName("só a sala, como antes: professorId nulo e lista de materiais vazia")
        void soASala() {
            var resposta = reservar(ana, cenario.criarSala(), null, null, "10:00", "12:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resposta.getBody().get("professorId").isNull()).isTrue();
            assertThat(resposta.getBody().get("materiaisIds")).isEmpty();
        }
    }

    @Nested
    @DisplayName("RF05: agenda do professor e materiais")
    class Conflitos {

        @Test
        @DisplayName("professor já alocado em outra sala no mesmo horário: 409 e nada é gravado")
        void professorOcupado() {
            long professor = professor();
            assertThat(reservar(ana, cenario.criarSala(), professor, List.of(), "10:00", "12:00").getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
            long outraSala = cenario.criarSala();

            var resposta = reservar(ana, outraSala, professor, List.of(), "11:00", "13:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody().get("detail").asText())
                    .isEqualTo("O professor Professor " + professor + " já está ocupado nesse horário. "
                            + "Escolha outro horário ou outro professor.");
            assertThat(cenario.contarReservas(outraSala)).as("tudo ou nada: a sala continua livre").isZero();
        }

        @Test
        @DisplayName("professor em aula que termina quando a outra começa: aceito (intervalos que se tocam)")
        void professorEncostado() {
            long professor = professor();
            reservar(ana, cenario.criarSala(), professor, List.of(), "08:00", "10:00");

            assertThat(reservar(ana, cenario.criarSala(), professor, List.of(), "10:00", "12:00").getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("MN-04: material em manutenção recusa a reserva inteira e é citado pelo patrimônio")
        void materialEmManutencao() {
            long projetor = material("BOM");
            var bloqueio = http.postForEntity("/api/bloqueios", new HttpEntity<>(Map.of("recursoId", projetor,
                    "tipo", "MANUTENCAO", "motivo", "Lâmpada queimada", "inicio", horario("08:00"),
                    "fim", horario("18:00")), cabecalhos(adm)), JsonNode.class);
            assertThat(bloqueio.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            long sala = cenario.criarSala();

            var resposta = reservar(ana, sala, null, List.of(projetor), "14:00", "16:00");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody().get("detail").asText())
                    .startsWith("O material Projetor (" + patrimonio(projetor) + ") está em manutenção das 08:00 às 18:00")
                    .endsWith("Escolha outro horário ou outro material.");
            assertThat(cenario.contarReservas(sala)).as("a sala continua livre").isZero();
        }

        @Test
        @DisplayName("material danificado ou professor desativado: 422 explicando o motivo")
        void naoReservaveis() {
            long danificado = material("DANIFICADO");
            long professor = professor();
            assertThat(http.postForEntity("/api/recursos/" + professor + "/desativacao",
                    new HttpEntity<>(Map.of(), cabecalhos(adm)), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);

            var comDanificado = reservar(ana, cenario.criarSala(), null, List.of(danificado), "10:00", "12:00");
            var comDesativado = reservar(ana, cenario.criarSala(), professor, List.of(), "10:00", "12:00");

            assertThat(comDanificado.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(comDanificado.getBody().get("detail").asText()).contains("está danificado");
            assertThat(comDesativado.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(comDesativado.getBody().get("detail").asText()).contains("está desativado");
        }

        @Test
        @DisplayName("id de outro tipo ou inexistente: 404 dizendo o que não foi encontrado")
        void naoEncontrados() {
            long sala = cenario.criarSala();
            long outraSala = cenario.criarSala();

            var salaComoProfessor = reservar(ana, sala, outraSala, List.of(), "10:00", "12:00");
            var materialInexistente = reservar(ana, sala, null, List.of(999_999_999L), "10:00", "12:00");

            assertThat(salaComoProfessor.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(salaComoProfessor.getBody().get("detail").asText())
                    .isEqualTo("Professor " + outraSala + " não encontrado.");
            assertThat(materialInexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(materialInexistente.getBody().get("detail").asText())
                    .isEqualTo("Material 999999999 não encontrado.");
        }

        @Test
        @DisplayName("materiais repetidos ou mais de 10: 400")
        void listaInvalida() {
            long projetor = material("BOM");
            List<Long> onze = LongStream.rangeClosed(1, 11).boxed().toList();

            var repetidos = reservar(ana, cenario.criarSala(), null, List.of(projetor, projetor), "10:00", "12:00");
            var demais = reservar(ana, cenario.criarSala(), null, onze, "10:00", "12:00");

            assertThat(repetidos.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(repetidos.getBody().get("erros").findValuesAsText("campo")).containsExactly("materiaisSemRepeticao");
            assertThat(demais.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(demais.getBody().get("erros").findValuesAsText("campo")).containsExactly("materiaisIds");
        }
    }

    @Nested
    @DisplayName("Concorrência (RC03)")
    class Concorrencia {

        static final int SOLICITACOES = 10;

        @RepeatedTest(value = 5, name = "rodada {currentRepetition} de {totalRepetitions}")
        @DisplayName("10 salas diferentes, o mesmo professor, ao mesmo tempo → exatamente 1 aceita, nenhum 500")
        void mesmoProfessor() throws Exception {
            long professor = professor();
            List<Long> salas = LongStream.range(0, SOLICITACOES).map(i -> cenario.criarSala()).boxed().toList();
            List<Supplier<ResponseEntity<JsonNode>>> pedidos = new ArrayList<>();
            for (long sala : salas) {
                String token = token("SOLICITANTE");
                pedidos.add(() -> reservar(token, sala, professor, List.of(), "10:00", "12:00"));
            }

            assertThat(statusSimultaneos(pedidos)).containsOnly(entry(201, 1L), entry(409, SOLICITACOES - 1L));
        }

        @RepeatedTest(value = 5, name = "rodada {currentRepetition} de {totalRepetitions}")
        @DisplayName("materiais pedidos em ordens opostas ([A, B] × [B, A]) → 1 aceita e sem deadlock")
        void ordemDeTravamento() throws Exception {
            long a = material("BOM");
            long b = material("BOM");
            List<Supplier<ResponseEntity<JsonNode>>> pedidos = new ArrayList<>();
            for (int i = 0; i < SOLICITACOES; i++) {
                long sala = cenario.criarSala();
                var ordem = i % 2 == 0 ? List.of(a, b) : List.of(b, a);
                String token = token("SOLICITANTE");
                pedidos.add(() -> reservar(token, sala, null, ordem, "10:00", "12:00"));
            }

            assertThat(statusSimultaneos(pedidos)).containsOnly(entry(201, 1L), entry(409, SOLICITACOES - 1L));
        }

        Map<Integer, Long> statusSimultaneos(List<Supplier<ResponseEntity<JsonNode>>> pedidos) throws Exception {
            var prontos = new CountDownLatch(pedidos.size());
            var largada = new CountDownLatch(1);
            List<Integer> status = new ArrayList<>();
            try (ExecutorService executor = Executors.newFixedThreadPool(pedidos.size())) {
                List<Future<ResponseEntity<JsonNode>>> futuros = new ArrayList<>();
                for (var pedido : pedidos) {
                    futuros.add(executor.submit(() -> {
                        prontos.countDown();
                        largada.await();
                        return pedido.get();
                    }));
                }
                assertThat(prontos.await(10, SECONDS)).isTrue();
                largada.countDown();
                for (var futuro : futuros) {
                    status.add(futuro.get(30, SECONDS).getStatusCode().value());
                }
            }
            return status.stream().collect(groupingBy(s -> s, counting()));
        }
    }

    // ---------------------------------------------------------------- utilitários

    String token(String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario(perfil), perfil);
    }

    /** Todo professor é restrito (D4): a reserva que o inclui aguarda aprovação. */
    long professor() {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Professor");
        corpo.put("matricula", UUID.randomUUID().toString().substring(0, 12));
        long id = criar("/api/professores", corpo);
        // nome com o id, para a mensagem de conflito ser verificável sem ambiguidade
        jdbc.update("UPDATE recurso SET nome = ? WHERE id = ?", "Professor " + id, id);
        return id;
    }

    long material(String estado) {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Projetor");
        corpo.put("restrito", false);
        corpo.put("patrimonio", UUID.randomUUID().toString().substring(0, 12));
        corpo.put("tipoMaterial", "Projetor");
        corpo.put("estadoConservacao", estado);
        return criar("/api/materiais", corpo);
    }

    String patrimonio(long material) {
        return jdbc.queryForObject("SELECT patrimonio FROM material WHERE id = ?", String.class, material);
    }

    long criar(String caminho, Map<String, Object> corpo) {
        var resposta = http.postForEntity(caminho, new HttpEntity<>(corpo, cabecalhos(adm)), JsonNode.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    ResponseEntity<JsonNode> reservar(String token, long sala, Long professor, List<Long> materiais,
                                      String inicio, String fim) {
        var corpo = new HashMap<String, Object>();
        corpo.put("salaId", sala);
        corpo.put("professorId", professor);
        corpo.put("materiaisIds", materiais);
        corpo.put("inicio", horario(inicio));
        corpo.put("fim", horario(fim));
        corpo.put("finalidade", "Aula");
        return http.postForEntity("/api/reservas", new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    int contarOcupacoes(long reserva) {
        return jdbc.queryForObject("SELECT count(*) FROM ocupacao WHERE reserva_id = ?", Integer.class, reserva);
    }

    static List<Long> ids(JsonNode lista) {
        List<Long> ids = new ArrayList<>();
        lista.forEach(id -> ids.add(id.asLong()));
        return ids;
    }

    static String horario(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(BRASILIA).toOffsetDateTime().toString();
    }

    static HttpHeaders cabecalhos(String token) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }
}
