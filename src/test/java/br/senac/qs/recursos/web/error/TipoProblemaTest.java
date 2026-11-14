package br.senac.qs.recursos.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("Catálogo de problemas e tradução das mensagens do framework")
class TipoProblemaTest {

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @CsvSource({
        "400, REQUISICAO_INVALIDA",
        "401, NAO_AUTENTICADO",
        "403, ACESSO_NEGADO",
        "404, NAO_ENCONTRADO",
        "405, METODO_NAO_SUPORTADO",
        "406, REQUISICAO_INVALIDA",
        // Decisão explícita: um 409 vindo do framework NÃO é "Conflito de horário".
        "409, REQUISICAO_INVALIDA",
        "415, FORMATO_NAO_SUPORTADO",
        "500, ERRO_INTERNO",
        "503, ERRO_INTERNO"
    })
    @DisplayName("status emitido pelo Spring MVC é mapeado para o tipo de problema correto")
    void mapeiaStatusParaTipo(int status, TipoProblema esperado) {
        assertThat(TipoProblema.paraStatus(HttpStatusCode.valueOf(status))).isEqualTo(esperado);
    }

    static Stream<Arguments> excecoesDoFramework() {
        return Stream.of(
            Arguments.of(new HttpRequestMethodNotSupportedException("DELETE"),
                    "O método DELETE não é suportado neste endereço."),
            Arguments.of(new HttpMediaTypeNotSupportedException("text/plain"),
                    "Formato de conteúdo não suportado. Envie os dados como application/json."),
            Arguments.of(new HttpMediaTypeNotAcceptableException(java.util.List.of(MediaType.APPLICATION_JSON)),
                    "Não é possível responder no formato solicitado pelo cabeçalho Accept."),
            Arguments.of(new NoResourceFoundException(HttpMethod.GET, "x"),
                    "O endereço solicitado não existe."),
            Arguments.of(new HttpMessageNotReadableException("falha interna do parser", new MockHttpInputMessage(new byte[0])),
                    GlobalExceptionHandler.DETALHE_CORPO_ILEGIVEL),
            Arguments.of(new MissingServletRequestParameterException("inicio", "OffsetDateTime"),
                    "O parâmetro obrigatório 'inicio' não foi informado."),
            Arguments.of(tipoInvalido(),
                    "O parâmetro 'capacidade' recebeu um valor em formato inválido."),
            Arguments.of(new IllegalStateException("qualquer outra"),
                    "Não foi possível processar a requisição."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("excecoesDoFramework")
    @DisplayName("cada exceção do framework recebe um detalhe em português, sem a mensagem técnica original")
    void traduzDetalhe(Exception excecao, String detalheEsperado) {
        assertThat(GlobalExceptionHandler.detalheEmPortugues(excecao))
                .isEqualTo(detalheEsperado)
                .doesNotContain("falha interna do parser");
    }

    @Test
    @DisplayName("criar() preenche type, title, status, instance e timestamp")
    void criaProblemDetailCompleto() {
        var agora = Instant.parse("2026-11-20T11:00:00Z");
        var problema = TipoProblema.CONFLITO_HORARIO.criar("ocupado", "/api/reservas", Clock.fixed(agora, ZoneOffset.UTC));

        assertThat(problema.getType()).hasToString("/problemas/conflito-horario");
        assertThat(problema.getTitle()).isEqualTo("Conflito de horário");
        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getDetail()).isEqualTo("ocupado");
        assertThat(problema.getInstance()).hasToString("/api/reservas");
        assertThat(problema.getProperties()).containsEntry(TipoProblema.PROPRIEDADE_TIMESTAMP, agora);
    }

    private static TypeMismatchException tipoInvalido() {
        var excecao = new TypeMismatchException("abc", Integer.class);
        excecao.initPropertyName("capacidade");
        return excecao;
    }
}
