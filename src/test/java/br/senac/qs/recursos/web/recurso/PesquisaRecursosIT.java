package br.senac.qs.recursos.web.recurso;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pesquisa de recursos (RF03): por tipo, capacidade, localização, competência e disponibilidade.
 *
 * <p>O banco é compartilhado entre os testes (reservas não podem ser apagadas), então cada teste
 * cria recursos com uma marca única (bloco, competência ou tipo de material) e filtra por ela.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Pesquisa de recursos via API")
class PesquisaRecursosIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.now(BRASILIA).plusDays(30);

    @Autowired
    TestRestTemplate http;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    String adm;
    String ana;
    String marca;

    @BeforeEach
    void prepararCenario() {
        var cenario = new CenarioBanco(jdbc);
        adm = GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario("ADMINISTRADOR"), "ADMINISTRADOR");
        ana = GeradorTokenTeste.token(jwtEncoder, cenario.criarUsuario("SOLICITANTE"), "SOLICITANTE");
        marca = UUID.randomUUID().toString().substring(0, 8);
    }

    @Nested
    @DisplayName("Salas: capacidade, localização e tipo")
    class Salas {

        @ParameterizedTest(name = "capacidade mínima {0} → {1}")
        @CsvSource(delimiter = '|', textBlock = """
            1  | 20,40,60
            39 | 40,60
            40 | 40,60
            41 | 60
            61 | ''
            """)
        @DisplayName("capacidade mínima inclui o próprio valor (fronteiras 39/40/41)")
        void capacidadeMinima(int minima, String esperadas) {
            sala("1", marca, 1, 20, "AULA");
            sala("2", marca, 1, 40, "AULA");
            sala("3", marca, 1, 60, "AULA");

            var resposta = pesquisar("bloco=" + marca + "&capacidadeMinima=" + minima);

            assertThat(valores(resposta, "capacidade"))
                    .containsExactlyElementsOf(esperadas.isEmpty() ? List.of() : List.of(esperadas.split(",")));
        }

        @Test
        @DisplayName("localização = bloco (sem diferenciar maiúsculas) e andar")
        void localizacao() {
            sala("1", marca, 1, 30, "AULA");
            sala("2", marca, 2, 30, "AULA");

            var resposta = pesquisar("bloco=" + marca.toUpperCase() + "&andar=2");

            assertThat(valores(resposta, "andar")).containsExactly("2");
        }

        @Test
        @DisplayName("tipo de sala")
        void tipoSala() {
            sala("1", marca, 1, 30, "AULA");
            sala("2", marca, 1, 30, "LABORATORIO");

            assertThat(valores(pesquisar("bloco=" + marca + "&tipoSala=LABORATORIO"), "tipoSala"))
                    .containsExactly("LABORATORIO");
        }

        @Test
        @DisplayName("recurso desativado não aparece")
        void inativo() {
            sala("1", marca, 1, 30, "AULA");
            long inativa = sala("2", marca, 1, 30, "AULA");
            assertThat(enviar(HttpMethod.POST, "/api/recursos/" + inativa + "/desativacao", adm, Map.of())
                    .getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(ids(pesquisar("bloco=" + marca))).doesNotContain(inativa).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Professores e materiais")
    class ProfessoresEMateriais {

        @Test
        @DisplayName("competência (sem diferenciar maiúsculas) só encontra professores que a têm")
        void competencia() {
            var kotlin = "Kotlin " + marca;
            long comKotlin = professor(List.of(kotlin, "Java"));
            professor(List.of("Java"));

            assertThat(ids(pesquisar("competencia=" + kotlin.toUpperCase()))).containsExactly(comKotlin);
            assertThat(ids(pesquisar("tipo=SALA&competencia=" + kotlin))).isEmpty();
        }

        @Test
        @DisplayName("tipo de material e tipo de recurso")
        void tipoMaterial() {
            var tipo = "Projetor " + marca;
            long projetor = material(tipo, "BOM");

            assertThat(ids(pesquisar("tipoMaterial=" + tipo.toLowerCase()))).containsExactly(projetor);
            assertThat(ids(pesquisar("tipo=MATERIAL&tipoMaterial=" + tipo))).containsExactly(projetor);
            assertThat(ids(pesquisar("tipo=PROFESSOR&tipoMaterial=" + tipo))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Disponibilidade no período [10:00, 12:00)")
    class Disponibilidade {

        static final String PERIODO = "&inicio=" + url(horario("10:00")) + "&fim=" + url(horario("12:00"));

        @Test
        @DisplayName("exclui quem tem reserva ou bloqueio sobreposto; encostar e reserva cancelada não ocupam")
        void ocupacao() {
            long livre = sala("1 livre", marca, 1, 30, "AULA");
            long reservada = sala("2 reservada", marca, 1, 30, "AULA");
            long encostada = sala("3 encostada", marca, 1, 30, "AULA");
            long emManutencao = sala("4 manutencao", marca, 1, 30, "AULA");
            long cancelada = sala("5 cancelada", marca, 1, 30, "AULA");
            reservar(reservada, "11:00", "13:00");
            reservar(encostada, "08:00", "10:00");
            reservar(encostada, "12:00", "13:00");
            bloquear(emManutencao, "11:59", "14:00");
            long reserva = reservar(cancelada, "10:00", "12:00");
            assertThat(enviar(HttpMethod.POST, "/api/reservas/" + reserva + "/cancelamento", ana, Map.of())
                    .getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(ids(pesquisar("bloco=" + marca + PERIODO))).containsExactly(livre, encostada, cancelada);
            assertThat(ids(pesquisar("bloco=" + marca)))
                    .as("sem período, a disponibilidade não filtra").hasSize(5);
        }

        @Test
        @DisplayName("material danificado não é oferecido para um período")
        void materialDanificado() {
            var tipo = "Notebook " + marca;
            long bom = material(tipo, "BOM");
            long danificado = material(tipo, "DANIFICADO");

            assertThat(ids(pesquisar("tipoMaterial=" + tipo + PERIODO))).containsExactly(bom);
            assertThat(ids(pesquisar("tipoMaterial=" + tipo))).containsExactlyInAnyOrder(bom, danificado);
        }
    }

    @Nested
    @DisplayName("Paginação e validação")
    class PaginacaoEValidacao {

        @Test
        @DisplayName("páginas em ordem de nome, com total de itens e de páginas")
        void paginacao() {
            long primeira = sala("1", marca, 1, 30, "AULA");
            long segunda = sala("2", marca, 1, 30, "AULA");
            long terceira = sala("3", marca, 1, 30, "AULA");

            var pagina0 = pesquisar("bloco=" + marca + "&tamanho=2");
            var pagina1 = pesquisar("bloco=" + marca + "&tamanho=2&pagina=1");

            assertThat(ids(pagina0)).containsExactly(primeira, segunda);
            assertThat(ids(pagina1)).containsExactly(terceira);
            assertThat(pagina0.getBody().get("pagina").asInt()).isZero();
            assertThat(pagina0.getBody().get("tamanho").asInt()).isEqualTo(2);
            assertThat(pagina0.getBody().get("totalItens").asLong()).isEqualTo(3);
            assertThat(pagina0.getBody().get("totalPaginas").asInt()).isEqualTo(2);
        }

        @ParameterizedTest(name = "{0} → campo {1}")
        @CsvSource(delimiter = '|', textBlock = """
            inicio=2030-01-01T10:00:00-03:00                                | periodoCompleto
            fim=2030-01-01T10:00:00-03:00                                   | periodoCompleto
            inicio=2030-01-01T10:00:00-03:00&fim=2030-01-01T10:00:00-03:00   | periodoValido
            tamanho=101                                                     | tamanho
            tamanho=0                                                       | tamanho
            pagina=-1                                                       | pagina
            capacidadeMinima=0                                              | capacidadeMinima
            capacidadeMinima=muitos                                         | capacidadeMinima
            tipo=CADEIRA                                                    | tipo
            """)
        @DisplayName("filtros inválidos: 400 indicando o campo, em português")
        void invalidos(String filtro, String campo) {
            var resposta = pesquisar(filtro.replace("+", "%2B").replace(":", "%3A"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            var erros = resposta.getBody().get("erros");
            assertThat(erros.findValuesAsText("campo")).containsExactly(campo);
            assertThat(erros.findValuesAsText("mensagem").getFirst())
                    .doesNotContain("Failed").doesNotContain("java.").doesNotContain("Exception");
        }

        @Test
        @DisplayName("exige login (401)")
        void exigeLogin() {
            var resposta = http.exchange("/api/recursos", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
                    JsonNode.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ---------------------------------------------------------------- utilitários

    ResponseEntity<JsonNode> pesquisar(String filtros) {
        return http.exchange(java.net.URI.create(http.getRootUri() + "/api/recursos?" + filtros.replace(" ", "%20")),
                HttpMethod.GET, new HttpEntity<>(cabecalhos(ana)), JsonNode.class);
    }

    long sala(String nome, String bloco, int andar, int capacidade, String tipoSala) {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", marca + " " + nome);
        corpo.put("restrito", false);
        corpo.put("capacidade", capacidade);
        corpo.put("bloco", bloco);
        corpo.put("andar", andar);
        corpo.put("tipoSala", tipoSala);
        return criar("/api/salas", corpo);
    }

    long professor(List<String> competencias) {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Professor " + marca);
        corpo.put("matricula", UUID.randomUUID().toString().substring(0, 12));
        corpo.put("competencias", competencias);
        return criar("/api/professores", corpo);
    }

    long material(String tipo, String estado) {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", tipo);
        corpo.put("restrito", false);
        corpo.put("patrimonio", UUID.randomUUID().toString().substring(0, 12));
        corpo.put("tipoMaterial", tipo);
        corpo.put("estadoConservacao", estado);
        return criar("/api/materiais", corpo);
    }

    long criar(String caminho, Map<String, Object> corpo) {
        var resposta = enviar(HttpMethod.POST, caminho, adm, corpo);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    long reservar(long sala, String inicio, String fim) {
        var resposta = enviar(HttpMethod.POST, "/api/reservas", ana, Map.of("salaId", sala,
                "inicio", horario(inicio), "fim", horario(fim), "finalidade", "Aula"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    void bloquear(long recurso, String inicio, String fim) {
        var resposta = enviar(HttpMethod.POST, "/api/bloqueios", adm, Map.of("recursoId", recurso,
                "tipo", "MANUTENCAO", "motivo", "Pintura", "inicio", horario(inicio), "fim", horario(fim)));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    ResponseEntity<JsonNode> enviar(HttpMethod metodo, String caminho, String token, Map<String, Object> corpo) {
        return http.exchange(caminho, metodo, new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    static List<Long> ids(ResponseEntity<JsonNode> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ids = new ArrayList<Long>();
        resposta.getBody().get("itens").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    static List<String> valores(ResponseEntity<JsonNode> resposta, String campo) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().get("itens").findValuesAsText(campo);
    }

    static String horario(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(BRASILIA).toOffsetDateTime().toString();
    }

    static String url(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    static HttpHeaders cabecalhos(String token) {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }
}
