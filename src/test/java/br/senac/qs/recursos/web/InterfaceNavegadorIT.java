package br.senac.qs.recursos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.senac.qs.recursos.suporte.CenarioBanco;
import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Interface web num navegador de verdade (Chromium sem janela, via Playwright): RF13 de ponta a
 * ponta. Diferente do {@code InterfaceIT}, aqui o JavaScript roda, a Content-Security-Policy é
 * aplicada pelo navegador e a largura da tela é a de um celular.
 *
 * <p>Cada teste usa um bloco de salas próprio, para a pesquisa não trazer salas de outros testes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Interface web no navegador (Playwright)")
class InterfaceNavegadorIT {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final String SENHA = "Senha2026";
    static final int LARGURA_CELULAR = 375;

    static Playwright playwright;
    static Browser navegador;

    @LocalServerPort
    int porta;

    @Autowired
    JdbcTemplate jdbc;

    CenarioBanco cenario;
    BrowserContext contexto;
    Page pagina;
    /** Erros do console, exceto falhas HTTP esperadas (ex.: 401 do login errado). */
    List<String> errosNoConsole;

    @BeforeAll
    static void abrirNavegador() {
        playwright = Playwright.create();
        navegador = playwright.chromium().launch();
    }

    @AfterAll
    static void fecharNavegador() {
        navegador.close();
        playwright.close();
    }

    @BeforeEach
    void abrirPagina() {
        cenario = new CenarioBanco(jdbc);
        contexto = navegador.newContext(new Browser.NewContextOptions()
                .setLocale("pt-BR")
                .setTimezoneId("America/Sao_Paulo")
                .setViewportSize(LARGURA_CELULAR, 812));
        pagina = contexto.newPage();
        errosNoConsole = new ArrayList<>();
        pagina.onConsoleMessage(mensagem -> {
            if ("error".equals(mensagem.type()) && !mensagem.text().startsWith("Failed to load resource")) {
                errosNoConsole.add(mensagem.text());
            }
        });
        pagina.navigate("http://localhost:" + porta + "/");
    }

    @AfterEach
    void fecharPagina() {
        contexto.close();
        assertThat(errosNoConsole).as("nenhum erro de JavaScript nem bloqueio da CSP").isEmpty();
    }

    @Test
    @DisplayName("fluxo na tela: solicitante reserva o laboratório, responsável aprova, solicitante vê aprovada")
    void reservaEAprovacao() {
        String bloco = unico("B");
        String laboratorio = "Laboratório " + bloco;
        cenario.criarSala(true, bloco, laboratorio);
        String professora = cenario.criarUsuarioComSenha("SOLICITANTE", SENHA);
        String responsavel = cenario.criarUsuarioComSenha("RESPONSAVEL", SENHA);
        LocalDate dia = LocalDate.now(BRASILIA).plusDays(7);

        entrar(professora);
        rotulo("Data").fill(dia.toString());
        rotulo("Início").fill("10:00");
        rotulo("Fim").fill("12:00");
        pagina.getByLabel("Bloco").fill(bloco);
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pesquisar salas livres")).click();
        var cartao = cartaoCom(laboratorio);
        PlaywrightAssertions.assertThat(cartao).containsText("Exige aprovação");
        cartao.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Escolher")).click();
        pagina.getByLabel("Finalidade").fill("Aula prática de redes");
        pagina.locator("#form-reserva").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Reservar"))
                .click();

        PlaywrightAssertions.assertThat(pagina.locator("#aviso")).hasText("Reserva registrada: aguardando aprovação.");
        PlaywrightAssertions.assertThat(cartaoCom(laboratorio)).containsText("Aguardando aprovação");
        assertThat(semRolagemLateral()).as("Minhas reservas cabe na largura do celular").isTrue();

        sair();
        entrar(responsavel);
        var pedido = cartaoCom(laboratorio);
        PlaywrightAssertions.assertThat(pedido).containsText("Solicitante: " + professora);
        pedido.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Aprovar")).click();
        PlaywrightAssertions.assertThat(pagina.locator("#aviso")).hasText("Reserva aprovada.");

        sair();
        entrar(professora);
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Minhas reservas")).click();
        PlaywrightAssertions.assertThat(cartaoCom(laboratorio)).containsText("Aprovada");
    }

    @Test
    @DisplayName("erro compreensível: senha errada mostra a mensagem da API e não entra")
    void senhaErrada() {
        String usuario = cenario.criarUsuarioComSenha("SOLICITANTE", SENHA);

        pagina.getByLabel("E-mail").fill(usuario);
        rotulo("Senha").fill("SenhaErrada1");
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar")).click();

        PlaywrightAssertions.assertThat(pagina.locator("#aviso")).hasText("E-mail ou senha inválidos.");
        PlaywrightAssertions.assertThat(pagina.locator("#menu")).isHidden();
    }

    @Test
    @DisplayName("nome de sala com HTML aparece como texto: o script injetado não executa")
    void nomeComHtmlNaoExecuta() {
        String bloco = unico("X");
        String nomeMalicioso = "<img src=x onerror=\"window.injetado=true\">Sala " + bloco;
        cenario.criarSala(false, bloco, nomeMalicioso);

        entrar(cenario.criarUsuarioComSenha("SOLICITANTE", SENHA));
        pagina.getByLabel("Bloco").fill(bloco);
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pesquisar salas livres")).click();

        var cartao = cartaoCom("Sala " + bloco);
        PlaywrightAssertions.assertThat(cartao.locator("strong")).hasText(nomeMalicioso);
        assertThat(cartao.locator("img").count()).as("nenhuma tag criada a partir do nome").isZero();
        assertThat(pagina.evaluate("() => window.injetado === undefined")).isEqualTo(true);
    }

    @Test
    @DisplayName("celular: relatório e telas do responsável sem rolagem lateral")
    void relatorioNoCelular() {
        entrar(cenario.criarUsuarioComSenha("RESPONSAVEL", SENHA));
        assertThat(semRolagemLateral()).as("Aprovações").isTrue();

        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Relatório")).click();
        rotulo("De").fill(LocalDate.now(BRASILIA).plusDays(7).toString());
        rotulo("Até").fill(LocalDate.now(BRASILIA).plusDays(7).toString());
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Gerar")).click();

        PlaywrightAssertions.assertThat(pagina.locator("#totais-relatorio")).containsText("Horas reservadas");
        assertThat(semRolagemLateral()).as("Relatório").isTrue();
    }

    // ---------------------------------------------------------------- utilitários

    /** Campo pelo rótulo exato ("Data" não pode casar com "Nova data"). */
    Locator rotulo(String texto) {
        return pagina.getByLabel(texto, new Page.GetByLabelOptions().setExact(true));
    }

    void entrar(String email) {
        pagina.getByLabel("E-mail").fill(email);
        rotulo("Senha").fill(SENHA);
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar")).click();
        PlaywrightAssertions.assertThat(pagina.locator("#usuario")).containsText(email);
    }

    void sair() {
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sair")).click();
        PlaywrightAssertions.assertThat(pagina.getByLabel("E-mail")).isVisible();
    }

    Locator cartaoCom(String texto) {
        return pagina.locator("article", new Page.LocatorOptions().setHasText(texto));
    }

    boolean semRolagemLateral() {
        return (Boolean) pagina.evaluate(
                "() => document.documentElement.scrollWidth <= document.documentElement.clientWidth");
    }

    static String unico(String prefixo) {
        return prefixo + UUID.randomUUID().toString().substring(0, 8);
    }
}
