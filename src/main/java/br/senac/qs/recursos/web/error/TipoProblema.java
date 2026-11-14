package br.senac.qs.recursos.web.error;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Catálogo de tipos de problema da API (RFC 9457).
 *
 * <p>Cada tipo tem um {@code type} estável (URI que o cliente pode usar para decidir o que
 * fazer), um título curto em português e o status HTTP correspondente. Centralizar aqui
 * garante respostas de erro consistentes em toda a API.
 */
public enum TipoProblema {

    REQUISICAO_INVALIDA("requisicao-invalida", HttpStatus.BAD_REQUEST, "Requisição inválida"),
    DADOS_INVALIDOS("dados-invalidos", HttpStatus.BAD_REQUEST, "Dados inválidos"),
    NAO_AUTENTICADO("nao-autenticado", HttpStatus.UNAUTHORIZED, "Não autenticado"),
    ACESSO_NEGADO("acesso-negado", HttpStatus.FORBIDDEN, "Acesso negado"),
    NAO_ENCONTRADO("nao-encontrado", HttpStatus.NOT_FOUND, "Não encontrado"),
    METODO_NAO_SUPORTADO("metodo-nao-suportado", HttpStatus.METHOD_NOT_ALLOWED, "Método não suportado"),
    FORMATO_NAO_SUPORTADO("formato-nao-suportado", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato não suportado"),
    CONFLITO_HORARIO("conflito-horario", HttpStatus.CONFLICT, "Conflito de horário"),
    EDICAO_CONCORRENTE("edicao-concorrente", HttpStatus.CONFLICT, "Edição concorrente"),
    VIOLACAO_INTEGRIDADE("violacao-integridade", HttpStatus.CONFLICT, "Violação de integridade"),
    REGRA_NEGOCIO("regra-negocio", HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada"),
    MUITAS_TENTATIVAS("muitas-tentativas", HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas"),
    ERRO_INTERNO("erro-interno", HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

    public static final String PREFIXO_TIPO = "/problemas/";
    public static final String PROPRIEDADE_TIMESTAMP = "timestamp";

    private final String identificador;
    private final HttpStatus status;
    private final String titulo;

    TipoProblema(String identificador, HttpStatus status, String titulo) {
        this.identificador = identificador;
        this.status = status;
        this.titulo = titulo;
    }

    public HttpStatus status() {
        return status;
    }

    public String titulo() {
        return titulo;
    }

    public URI tipo() {
        return URI.create(PREFIXO_TIPO + identificador);
    }

    public ProblemDetail criar(String detalhe, String caminho, Clock clock) {
        var problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(tipo());
        problema.setTitle(titulo);
        problema.setInstance(URI.create(caminho));
        problema.setProperty(PROPRIEDADE_TIMESTAMP, Instant.now(clock));
        return problema;
    }

    /**
     * Tipo correspondente a um status emitido pelo próprio Spring MVC (rota inexistente, método
     * errado etc.). Mapeamento explícito de propósito: um 409 genérico do framework, por exemplo,
     * não deve ser apresentado como "Conflito de horário". Demais status caem em
     * {@link #REQUISICAO_INVALIDA} (4xx) ou {@link #ERRO_INTERNO} (5xx).
     */
    public static TipoProblema paraStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> NAO_AUTENTICADO;
            case 403 -> ACESSO_NEGADO;
            case 404 -> NAO_ENCONTRADO;
            case 405 -> METODO_NAO_SUPORTADO;
            case 415 -> FORMATO_NAO_SUPORTADO;
            default -> status.is5xxServerError() ? ERRO_INTERNO : REQUISICAO_INVALIDA;
        };
    }
}
