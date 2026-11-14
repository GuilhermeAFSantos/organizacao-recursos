package br.senac.qs.recursos.web.reserva;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * RC03: N solicitações SIMULTÂNEAS para o mesmo recurso e horário produzem UMA única reserva.
 *
 * <p>Como o teste força a simultaneidade:
 * <ol>
 *   <li>Cada uma das N threads se prepara (token próprio, corpo pronto), avisa que está pronta
 *       ({@code prontos.countDown()}) e PARA no portão de largada ({@code largada.await()}).</li>
 *   <li>A thread principal espera todas ficarem prontas e abre o portão ({@code largada.countDown()}):
 *       as N requisições HTTP saem praticamente no mesmo instante.</li>
 * </ol>
 * Repetido em várias rodadas (cada uma com sala nova) para não depender de uma única execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("RC03: solicitações simultâneas para o mesmo recurso e horário")
class ReservaConcorrenciaIT {

    static final int SOLICITACOES = 20;

    record Resultado(int status, String titulo) {
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @RepeatedTest(value = 3, name = "rodada {currentRepetition} de {totalRepetitions}")
    @DisplayName("N requisições simultâneas → exatamente 1 aceita (201) e N−1 recusadas (409 Conflito de horário)")
    void exatamenteUmaReservaAceita() throws Exception {
        var cenario = new CenarioBanco(jdbc);
        long sala = cenario.criarSala();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < SOLICITACOES; i++) {
            tokens.add(GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario("SOLICITANTE"), "SOLICITANTE"));
        }

        var prontos = new CountDownLatch(SOLICITACOES);
        var largada = new CountDownLatch(1);
        List<Resultado> resultados = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(SOLICITACOES)) {
            List<Future<Resultado>> futuros = new ArrayList<>();
            for (String token : tokens) {
                futuros.add(executor.submit(() -> {
                    prontos.countDown();
                    largada.await();
                    return reservar(token, sala);
                }));
            }
            assertThat(prontos.await(10, SECONDS)).as("todas as threads prontas").isTrue();
            largada.countDown();
            for (Future<Resultado> futuro : futuros) {
                resultados.add(futuro.get(30, SECONDS));
            }
        }

        Map<Integer, Long> quantidadePorStatus = resultados.stream()
                .collect(groupingBy(Resultado::status, counting()));
        assertThat(quantidadePorStatus)
                .as("exatamente 1 aceita e as demais recusadas; nenhum 500")
                .containsOnly(entry(201, 1L), entry(409, SOLICITACOES - 1L));
        assertThat(resultados).filteredOn(r -> r.status() == 409)
                .extracting(Resultado::titulo)
                .as("toda recusa é apresentada ao usuário como conflito de horário")
                .containsOnly("Conflito de horário");

        assertThat(cenario.contarReservas(sala)).as("reservas gravadas").isEqualTo(1);
        assertThat(cenario.contarOcupacoesBloqueantes(sala)).as("ocupações bloqueantes").isEqualTo(1);
    }

    private Resultado reservar(String token, long sala) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        var resposta = http.postForEntity(CriarReservaSalaIT.URL,
                new HttpEntity<>(CriarReservaSalaIT.corpo(sala, "08:00:00", "10:00:00"), cabecalhos), JsonNode.class);
        JsonNode corpo = resposta.getBody();
        String titulo = corpo != null && corpo.has("title") ? corpo.get("title").asText() : null;
        return new Resultado(resposta.getStatusCode().value(), titulo);
    }
}
