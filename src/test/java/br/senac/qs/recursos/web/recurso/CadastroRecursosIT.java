package br.senac.qs.recursos.web.recurso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

/** Cadastro e consulta de salas, professores e materiais via API (RF02), com auditoria (RF10). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Cadastro de recursos via API")
class CadastroRecursosIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final LocalDate DIA = LocalDate.now(BRASILIA).plusDays(30);

    @Autowired
    TestRestTemplate http;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    String emailAdm;
    String adm;
    String ana;
    String rita;

    @BeforeEach
    void prepararCenario() {
        cenario = new CenarioBanco(jdbc);
        emailAdm = cenario.criarUsuario("ADMINISTRADOR");
        adm = token(emailAdm, "ADMINISTRADOR");
        ana = token(cenario.criarUsuario("SOLICITANTE"), "SOLICITANTE");
        rita = token(cenario.criarUsuario("RESPONSAVEL"), "RESPONSAVEL");
    }

    @Nested
    @DisplayName("Salas")
    class Salas {

        @Test
        @DisplayName("ADMINISTRADOR cadastra: 201, Location, dados devolvidos e auditoria")
        void cadastra() {
            var nome = unico("Sala");

            var resposta = enviar(HttpMethod.POST, "/api/salas", adm, sala(nome, 40));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            var corpo = resposta.getBody();
            long id = corpo.get("id").asLong();
            assertThat(resposta.getHeaders().getLocation()).hasPath("/api/recursos/" + id);
            assertThat(corpo.get("tipo").asText()).isEqualTo("SALA");
            assertThat(corpo.get("nome").asText()).isEqualTo(nome);
            assertThat(corpo.get("capacidade").asInt()).isEqualTo(40);
            assertThat(corpo.get("bloco").asText()).isEqualTo("A");
            assertThat(corpo.get("andar").asInt()).isEqualTo(2);
            assertThat(corpo.get("tipoSala").asText()).isEqualTo("LABORATORIO");
            assertThat(corpo.get("restrito").asBoolean()).isTrue();
            assertThat(corpo.get("ativo").asBoolean()).isTrue();
            assertThat(corpo.has("matricula")).as("campos de outros tipos ficam de fora").isFalse();
            assertThat(auditoria(id)).containsExactly("CRIACAO por " + emailAdm);
        }

        @Test
        @DisplayName("SOLICITANTE e RESPONSAVEL não cadastram (403); sem token, 401")
        void semPermissao() {
            assertThat(enviar(HttpMethod.POST, "/api/salas", ana, sala(unico("Sala"), 10)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(enviar(HttpMethod.POST, "/api/salas", rita, sala(unico("Sala"), 10)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(enviar(HttpMethod.POST, "/api/salas", null, sala(unico("Sala"), 10)).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("dados inválidos: 400 listando cada campo")
        void dadosInvalidos() {
            var corpo = new HashMap<String, Object>();
            corpo.put("nome", " ");
            corpo.put("capacidade", 0);

            var resposta = enviar(HttpMethod.POST, "/api/salas", adm, corpo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(campos(resposta)).containsExactlyInAnyOrder("andar", "bloco", "capacidade", "nome",
                    "restrito", "tipoSala");
        }

        @Test
        @DisplayName("tipo de sala fora da lista: 400 sem detalhe interno")
        void tipoInvalido() {
            var corpo = sala(unico("Sala"), 10);
            corpo.put("tipoSala", "COZINHA");

            var resposta = enviar(HttpMethod.POST, "/api/salas", adm, corpo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody().get("detail").asText()).doesNotContain("TipoSala").doesNotContain("java");
        }

        @Test
        @DisplayName("nome repetido, mesmo com outras maiúsculas: 409")
        void nomeDuplicado() {
            var nome = unico("Sala");
            criar("/api/salas", sala(nome, 10));

            var resposta = enviar(HttpMethod.POST, "/api/salas", adm, sala(nome.toUpperCase(), 10));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(resposta.getBody().get("detail").asText())
                    .isEqualTo("Já existe uma sala com o nome " + nome.toUpperCase() + ".");
        }

        @Test
        @DisplayName("alteração: 200, dados novos e auditoria; pode manter o próprio nome")
        void altera() {
            var nome = unico("Sala");
            long id = criar("/api/salas", sala(nome, 10));
            var novo = sala(nome.toLowerCase(), 60);
            novo.put("restrito", false);

            var resposta = enviar(HttpMethod.PUT, "/api/salas/" + id, adm, novo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody().get("capacidade").asInt()).isEqualTo(60);
            assertThat(resposta.getBody().get("restrito").asBoolean()).isFalse();
            assertThat(auditoria(id)).containsExactly("CRIACAO por " + emailAdm, "ALTERACAO por " + emailAdm);
        }

        @Test
        @DisplayName("alteração para o nome de outra sala: 409")
        void alteraParaNomeDeOutra() {
            var nomeOutra = unico("Sala");
            criar("/api/salas", sala(nomeOutra, 10));
            long id = criar("/api/salas", sala(unico("Sala"), 10));

            assertThat(enviar(HttpMethod.PUT, "/api/salas/" + id, adm, sala(nomeOutra, 10)).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("alterar como sala o id de outro tipo de recurso, ou um id inexistente: 404")
        void naoEncontrada() {
            long professor = criar("/api/professores", professor(unico("P"), List.of()));

            assertThat(enviar(HttpMethod.PUT, "/api/salas/" + professor, adm, sala(unico("Sala"), 10))
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(enviar(HttpMethod.PUT, "/api/salas/999999999", adm, sala(unico("Sala"), 10))
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Professores")
    class Professores {

        @Test
        @DisplayName("competências repetidas viram uma só, em ordem alfabética, e são reaproveitadas entre professores")
        void competencias() {
            var java = unico("Java");
            var bd = unico("Banco de Dados");

            var resposta = enviar(HttpMethod.POST, "/api/professores", adm,
                    professor(unico("P"), List.of(java, " " + java.toLowerCase() + " ", bd)));
            criar("/api/professores", professor(unico("P"), List.of(java.toUpperCase())));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resposta.getBody().get("tipo").asText()).isEqualTo("PROFESSOR");
            assertThat(resposta.getBody().get("email").asText()).isEqualTo("carlos@senac.br");
            assertThat(textos(resposta.getBody().get("competencias"))).containsExactly(bd, java);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM competencia WHERE lower(nome) = lower(?)",
                    Integer.class, java)).isEqualTo(1);
        }

        @Test
        @DisplayName("matrícula repetida: 409; e-mail inválido: 400")
        void validacoes() {
            var matricula = unico("P");
            criar("/api/professores", professor(matricula, List.of()));

            var duplicado = enviar(HttpMethod.POST, "/api/professores", adm, professor(matricula, List.of()));
            var corpo = professor(unico("P"), List.of());
            corpo.put("email", "nao-e-email");
            var emailInvalido = enviar(HttpMethod.POST, "/api/professores", adm, corpo);

            assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(duplicado.getBody().get("detail").asText())
                    .isEqualTo("Já existe um professor com a matrícula " + matricula + ".");
            assertThat(emailInvalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(campos(emailInvalido)).containsExactly("email");
        }

        @Test
        @DisplayName("alteração substitui as competências")
        void altera() {
            long id = criar("/api/professores", professor(unico("P"), List.of(unico("Java"))));
            var redes = unico("Redes");

            var resposta = enviar(HttpMethod.PUT, "/api/professores/" + id, adm, professor(unico("P"), List.of(redes)));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(textos(resposta.getBody().get("competencias"))).containsExactly(redes);
        }
    }

    @Nested
    @DisplayName("Materiais")
    class Materiais {

        @Test
        @DisplayName("cadastra; patrimônio repetido: 409")
        void cadastra() {
            var patrimonio = unico("PAT");

            var resposta = enviar(HttpMethod.POST, "/api/materiais", adm, material(patrimonio, "BOM"));
            var duplicado = enviar(HttpMethod.POST, "/api/materiais", adm, material(patrimonio, "BOM"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(resposta.getBody().get("tipo").asText()).isEqualTo("MATERIAL");
            assertThat(resposta.getBody().get("patrimonio").asText()).isEqualTo(patrimonio);
            assertThat(resposta.getBody().get("tipoMaterial").asText()).isEqualTo("Projetor");
            assertThat(resposta.getBody().get("estadoConservacao").asText()).isEqualTo("BOM");
            assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(duplicado.getBody().get("detail").asText())
                    .isEqualTo("Já existe um material com o patrimônio " + patrimonio + ".");
        }

        @Test
        @DisplayName("alteração registra o estado de conservação")
        void altera() {
            var patrimonio = unico("PAT");
            long id = criar("/api/materiais", material(patrimonio, "BOM"));

            var resposta = enviar(HttpMethod.PUT, "/api/materiais/" + id, adm, material(patrimonio, "DANIFICADO"));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody().get("estadoConservacao").asText()).isEqualTo("DANIFICADO");
        }
    }

    @Nested
    @DisplayName("Desativação e reativação")
    class Desativacao {

        @Test
        @DisplayName("sala desativada recusa reservas com mensagem clara; reativada volta a aceitar; tudo auditado")
        void cicloCompleto() {
            long id = criar("/api/salas", sala(unico("Sala"), 10));

            var desativada = enviar(HttpMethod.POST, "/api/recursos/" + id + "/desativacao", adm, Map.of());
            var recusada = reservar(id, "08:00", "09:00");
            var reativada = enviar(HttpMethod.POST, "/api/recursos/" + id + "/reativacao", adm, Map.of());
            var aceita = reservar(id, "08:00", "09:00");

            assertThat(desativada.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(desativada.getBody().get("ativo").asBoolean()).isFalse();
            assertThat(recusada.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(recusada.getBody().get("detail").asText()).contains("está desativada");
            assertThat(reativada.getBody().get("ativo").asBoolean()).isTrue();
            assertThat(aceita.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(auditoria(id)).containsExactly("CRIACAO por " + emailAdm, "DESATIVACAO por " + emailAdm,
                    "REATIVACAO por " + emailAdm);
        }

        @Test
        @DisplayName("recurso com reserva ativa que ainda não terminou não é desativado (422)")
        void comReservaFutura() {
            long id = criar("/api/salas", sala(unico("Sala"), 10));
            assertThat(reservar(id, "08:00", "09:00").getStatusCode()).isEqualTo(HttpStatus.CREATED);

            var resposta = enviar(HttpMethod.POST, "/api/recursos/" + id + "/desativacao", adm, Map.of());

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(resposta.getBody().get("detail").asText()).isEqualTo(
                    "Não é possível desativar o recurso: há 1 reserva(s) ativa(s) que ainda não terminaram. "
                            + "Cancele-as antes.");
        }

        @Test
        @DisplayName("reserva cancelada não impede a desativação")
        void comReservaCancelada() {
            long id = criar("/api/salas", sala(unico("Sala"), 10));
            long reserva = reservar(id, "08:00", "09:00").getBody().get("id").asLong();
            assertThat(enviar(HttpMethod.POST, "/api/reservas/" + reserva + "/cancelamento", ana, Map.of())
                    .getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(enviar(HttpMethod.POST, "/api/recursos/" + id + "/desativacao", adm, Map.of()).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"desativacao", "reativacao"})
        @DisplayName("SOLICITANTE não desativa nem reativa (403)")
        void semPermissao(String acao) {
            long id = criar("/api/salas", sala(unico("Sala"), 10));

            assertThat(enviar(HttpMethod.POST, "/api/recursos/" + id + "/" + acao, ana, Map.of()).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("Consulta por id")
    class Consulta {

        @Test
        @DisplayName("qualquer usuário logado consulta; id inexistente: 404")
        void consulta() {
            long id = criar("/api/materiais", material(unico("PAT"), "REGULAR"));

            var resposta = http.exchange("/api/recursos/" + id, HttpMethod.GET, new HttpEntity<>(cabecalhos(ana)),
                    JsonNode.class);
            var inexistente = http.exchange("/api/recursos/999999999", HttpMethod.GET,
                    new HttpEntity<>(cabecalhos(ana)), JsonNode.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody().get("estadoConservacao").asText()).isEqualTo("REGULAR");
            assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ---------------------------------------------------------------- utilitários

    String token(String email, String perfil) {
        return GeradorTokenTeste.token(jwtEncoder, email, perfil);
    }

    static String unico(String prefixo) {
        return prefixo + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    static Map<String, Object> sala(String nome, int capacidade) {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", nome);
        corpo.put("restrito", true);
        corpo.put("capacidade", capacidade);
        corpo.put("bloco", "A");
        corpo.put("andar", 2);
        corpo.put("tipoSala", "LABORATORIO");
        return corpo;
    }

    static Map<String, Object> professor(String matricula, List<String> competencias) {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Carlos Lima");
        corpo.put("restrito", true);
        corpo.put("matricula", matricula);
        corpo.put("email", " Carlos@Senac.br ");
        corpo.put("competencias", competencias);
        return corpo;
    }

    static Map<String, Object> material(String patrimonio, String estado) {
        var corpo = new HashMap<String, Object>();
        corpo.put("nome", "Projetor Epson");
        corpo.put("restrito", false);
        corpo.put("patrimonio", patrimonio);
        corpo.put("tipoMaterial", "Projetor");
        corpo.put("estadoConservacao", estado);
        return corpo;
    }

    long criar(String caminho, Map<String, Object> corpo) {
        var resposta = enviar(HttpMethod.POST, caminho, adm, corpo);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").asLong();
    }

    ResponseEntity<JsonNode> reservar(long sala, String inicio, String fim) {
        Map<String, Object> corpo = Map.of("salaId", sala, "inicio", horario(inicio), "fim", horario(fim),
                "finalidade", "Aula");
        return enviar(HttpMethod.POST, "/api/reservas", ana, corpo);
    }

    ResponseEntity<JsonNode> enviar(HttpMethod metodo, String caminho, String token, Map<String, Object> corpo) {
        return http.exchange(caminho, metodo, new HttpEntity<>(corpo, cabecalhos(token)), JsonNode.class);
    }

    List<String> auditoria(long recurso) {
        return jdbc.queryForList("""
                SELECT acao || ' por ' || usuario_email FROM registro_auditoria
                WHERE entidade = 'RECURSO' AND entidade_id = ? ORDER BY id""", String.class, recurso);
    }

    static List<String> campos(ResponseEntity<JsonNode> resposta) {
        return resposta.getBody().get("erros").findValuesAsText("campo");
    }

    static List<String> textos(JsonNode lista) {
        var textos = new java.util.ArrayList<String>();
        lista.forEach(item -> textos.add(item.asText()));
        return textos;
    }

    static String horario(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(BRASILIA).toOffsetDateTime().toString();
    }

    static HttpHeaders cabecalhos(String token) {
        var cabecalhos = new HttpHeaders();
        if (token != null) {
            cabecalhos.setBearerAuth(token);
        }
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return cabecalhos;
    }
}
