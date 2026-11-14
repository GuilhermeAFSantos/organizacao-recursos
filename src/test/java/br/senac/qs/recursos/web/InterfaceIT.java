package br.senac.qs.recursos.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.senac.qs.recursos.suporte.PostgresTestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * Contrato da interface web (RF13): o que dá para garantir sem um navegador. A página é servida pela
 * própria aplicação, é responsiva (viewport), não tem código inline e é protegida por
 * Content-Security-Policy; o JavaScript nunca injeta dados como HTML.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Interface web: contrato estático e segurança")
class InterfaceIT {

    @Autowired
    TestRestTemplate http;

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "/,             text/html",
        "/css/app.css,  text/css",
        "/js/app.js,    text/javascript"
    })
    @DisplayName("página, estilo e script são servidos sem login, com o tipo certo")
    void arquivos(String caminho, String tipo) {
        var resposta = http.getForEntity(caminho, String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType().isCompatibleWith(MediaType.parseMediaType(tipo)))
                .as("Content-Type %s", resposta.getHeaders().getContentType()).isTrue();
    }

    @Test
    @DisplayName("responsiva e sem código inline: viewport, CSS e JS em arquivos próprios")
    void semCodigoInline() {
        var pagina = http.getForObject("/", String.class);

        assertThat(pagina).contains("name=\"viewport\"").contains("lang=\"pt-BR\"")
                .contains("href=\"css/app.css\"").contains("src=\"js/app.js\"");
        assertThat(pagina).as("sem <style> inline").doesNotContain("<style");
        assertThat(pagina).as("todo <script> aponta para um arquivo")
                .doesNotContainPattern("<script(?![^>]*\\ssrc=)[^>]*>");
        assertThat(pagina).as("sem tratadores de evento inline").doesNotContainPattern("\\son[a-z]+=");
    }

    @Test
    @DisplayName("Content-Security-Policy: só scripts e estilos da própria origem; a página não pode ser embutida")
    void politicaDeSeguranca() {
        var cabecalhos = http.getForEntity("/", String.class).getHeaders();

        assertThat(cabecalhos.getFirst("Content-Security-Policy"))
                .contains("default-src 'self'")
                .contains("script-src 'self'")
                .contains("style-src 'self'")
                .contains("frame-ancestors 'none'")
                .doesNotContain("unsafe-inline")
                .doesNotContain("unsafe-eval");
        assertThat(cabecalhos.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(cabecalhos.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    @DisplayName("o Swagger UI continua funcionando (fica fora da política estrita, que ele não suporta)")
    void swaggerForaDaPolitica() {
        var resposta = http.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getFirst("Content-Security-Policy")).isNull();
    }

    @Test
    @DisplayName("o JavaScript nunca usa innerHTML/outerHTML/insertAdjacentHTML/eval: dados entram só como texto")
    void semInjecaoDeHtml() {
        var script = http.getForObject("/js/app.js", String.class);

        assertThat(script).isNotBlank()
                .doesNotContain("innerHTML").doesNotContain("outerHTML")
                .doesNotContain("insertAdjacentHTML").doesNotContain("eval(")
                .doesNotContain("document.write");
        assertThat(script).as("o token fica na sessão da aba, não no armazenamento permanente")
                .contains("sessionStorage").doesNotContain("localStorage");
    }
}
