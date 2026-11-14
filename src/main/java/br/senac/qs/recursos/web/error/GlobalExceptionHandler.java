package br.senac.qs.recursos.web.error;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.exception.CredenciaisInvalidasException;
import br.senac.qs.recursos.domain.exception.MuitasTentativasException;
import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratamento global e seguro de erros (RF13 + requisito não funcional de segurança).
 *
 * <p>Princípios:
 * <ol>
 *   <li>Toda resposta de erro é um {@link ProblemDetail} (RFC 9457) com título e detalhe em português.</li>
 *   <li>Nada interno vaza: sem stack trace, SQL, nome de classe ou mensagem de exceção técnica.</li>
 *   <li>Erros inesperados (500) recebem um {@code codigoErro} aleatório, que também vai para o log.
 *       O usuário informa o código ao suporte e a equipe encontra o stack trace completo no log.</li>
 * </ol>
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para reaproveitar o mapeamento de status das
 * exceções do Spring MVC (404, 405, 415...), trocando só o conteúdo pelo nosso padrão.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String PROPRIEDADE_ERROS = "erros";
    public static final String PROPRIEDADE_CODIGO_ERRO = "codigoErro";
    public static final String PROPRIEDADE_RESERVAS_EM_CONFLITO = "reservasEmConflito";

    static final String DETALHE_DADOS_INVALIDOS = "Um ou mais campos estão inválidos. Corrija-os e tente novamente.";
    static final String DETALHE_CORPO_ILEGIVEL = "O corpo da requisição está ausente ou malformado. Verifique o JSON, "
            + "os nomes dos campos e o formato das datas (ISO-8601 com fuso, ex.: 2026-11-20T08:00:00-03:00).";
    static final String DETALHE_EDICAO_CONCORRENTE = "Este registro foi alterado por outra pessoa enquanto você "
            + "trabalhava nele. Recarregue os dados e tente novamente.";
    static final String DETALHE_VIOLACAO_INTEGRIDADE = "A operação não pôde ser concluída porque viola uma regra de "
            + "integridade dos dados (por exemplo, um valor que deveria ser único já está cadastrado).";
    static final String DETALHE_ACESSO_NEGADO = "Seu perfil não tem permissão para realizar esta operação.";
    static final String DETALHE_ERRO_INTERNO = "Ocorreu um erro inesperado. Se o problema persistir, informe o "
            + "código %s ao suporte.";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /** Item da lista "erros" em respostas de validação. */
    public record ErroCampo(String campo, String mensagem) {
    }

    // ---------------------------------------------------------------- exceções de domínio

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<ProblemDetail> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest req) {
        return responder(TipoProblema.NAO_ENCONTRADO.criar(ex.getMessage(), req.getRequestURI(), clock));
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    ResponseEntity<ProblemDetail> credenciaisInvalidas(CredenciaisInvalidasException ex, HttpServletRequest req) {
        return responder(TipoProblema.NAO_AUTENTICADO.criar(ex.getMessage(), req.getRequestURI(), clock));
    }

    @ExceptionHandler(MuitasTentativasException.class)
    ResponseEntity<ProblemDetail> muitasTentativas(MuitasTentativasException ex, HttpServletRequest req) {
        return responder(TipoProblema.MUITAS_TENTATIVAS.criar(ex.getMessage(), req.getRequestURI(), clock));
    }

    /** Perfil até pode agir, mas não sobre ESTA reserva (ex.: cancelar a reserva de outra pessoa). */
    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    ResponseEntity<ProblemDetail> operacaoNaoPermitida(OperacaoNaoPermitidaException ex, HttpServletRequest req) {
        return responder(TipoProblema.ACESSO_NEGADO.criar(ex.getMessage(), req.getRequestURI(), clock));
    }

    @ExceptionHandler(ConflitoHorarioException.class)
    ResponseEntity<ProblemDetail> conflitoHorario(ConflitoHorarioException ex, HttpServletRequest req) {
        var problema = TipoProblema.CONFLITO_HORARIO.criar(ex.getMessage(), req.getRequestURI(), clock);
        if (!ex.getReservasEmConflito().isEmpty()) {
            problema.setProperty(PROPRIEDADE_RESERVAS_EM_CONFLITO, ex.getReservasEmConflito());
        }
        return responder(problema);
    }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<ProblemDetail> regraNegocio(RegraNegocioException ex, HttpServletRequest req) {
        return responder(TipoProblema.REGRA_NEGOCIO.criar(ex.getMessage(), req.getRequestURI(), clock));
    }

    // ---------------------------------------------------------------- persistência e segurança

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> edicaoConcorrente(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return responder(TipoProblema.EDICAO_CONCORRENTE.criar(DETALHE_EDICAO_CONCORRENTE, req.getRequestURI(), clock));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> violacaoIntegridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        // A causa (SQL, nome da constraint) vai só para o log, nunca para a resposta.
        log.warn("Violação de integridade em {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(TipoProblema.VIOLACAO_INTEGRIDADE.criar(DETALHE_VIOLACAO_INTEGRIDADE, req.getRequestURI(), clock));
    }

    /**
     * Negação vinda de {@code @PreAuthorize} dentro do controller. Sem este handler, o
     * {@link #erroInesperado} capturaria a exceção e devolveria 500 em vez de 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> acessoNegado(AccessDeniedException ex, HttpServletRequest req) {
        return responder(TipoProblema.ACESSO_NEGADO.criar(DETALHE_ACESSO_NEGADO, req.getRequestURI(), clock));
    }

    /** Validação de parâmetros em beans {@code @Validated} (ex.: services), fora do corpo HTTP. */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> restricaoViolada(ConstraintViolationException ex, HttpServletRequest req) {
        var erros = ex.getConstraintViolations().stream()
                .map(v -> new ErroCampo(ultimoSegmento(v.getPropertyPath().toString()), v.getMessage()))
                .sorted(Comparator.comparing(ErroCampo::campo))
                .toList();
        return responder(dadosInvalidos(erros, req.getRequestURI()));
    }

    // ---------------------------------------------------------------- rede de segurança (500)

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> erroInesperado(Exception ex, HttpServletRequest req) {
        String codigo = UUID.randomUUID().toString();
        log.error("Erro inesperado [codigoErro={}] em {} {}", codigo, req.getMethod(), req.getRequestURI(), ex);
        var problema = TipoProblema.ERRO_INTERNO.criar(DETALHE_ERRO_INTERNO.formatted(codigo), req.getRequestURI(), clock);
        problema.setProperty(PROPRIEDADE_CODIGO_ERRO, codigo);
        return responder(problema);
    }

    // ---------------------------------------------------------------- exceções do Spring MVC

    /** Bean Validation no corpo ({@code @Valid}): lista cada campo inválido com a mensagem. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        var erros = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErroCampo(f.getField(), f.getDefaultMessage()))
                .sorted(Comparator.comparing(ErroCampo::campo))
                .toList();
        return ResponseEntity.badRequest().headers(headers).body(dadosInvalidos(erros, caminho(request)));
    }

    /**
     * Ponto único por onde passam as demais exceções do Spring MVC. O corpo padrão (em inglês)
     * é substituído por um ProblemDetail do nosso catálogo, mantendo o status original.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, Object body,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        var problema = TipoProblema.paraStatus(status).criar(detalheEmPortugues(ex), caminho(request), clock);
        problema.setStatus(status.value());
        return super.handleExceptionInternal(ex, problema, headers, status, request);
    }

    static String detalheEmPortugues(Exception ex) {
        return switch (ex) {
            case HttpRequestMethodNotSupportedException e ->
                    "O método %s não é suportado neste endereço.".formatted(e.getMethod());
            case HttpMediaTypeNotSupportedException e ->
                    "Formato de conteúdo não suportado. Envie os dados como application/json.";
            case HttpMediaTypeNotAcceptableException e ->
                    "Não é possível responder no formato solicitado pelo cabeçalho Accept.";
            case NoResourceFoundException e -> "O endereço solicitado não existe.";
            case HttpMessageNotReadableException e -> DETALHE_CORPO_ILEGIVEL;
            case MissingServletRequestParameterException e ->
                    "O parâmetro obrigatório '%s' não foi informado.".formatted(e.getParameterName());
            case TypeMismatchException e ->
                    "O parâmetro '%s' recebeu um valor em formato inválido.".formatted(e.getPropertyName());
            case HandlerMethodValidationException e -> "Um ou mais parâmetros da requisição são inválidos.";
            default -> "Não foi possível processar a requisição.";
        };
    }

    // ---------------------------------------------------------------- utilitários

    private ProblemDetail dadosInvalidos(List<ErroCampo> erros, String caminho) {
        var problema = TipoProblema.DADOS_INVALIDOS.criar(DETALHE_DADOS_INVALIDOS, caminho, clock);
        problema.setProperty(PROPRIEDADE_ERROS, erros);
        return problema;
    }

    private static ResponseEntity<ProblemDetail> responder(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus()).body(problema);
    }

    /** Na pilha Servlet do Spring MVC, o WebRequest é sempre um ServletWebRequest. */
    private static String caminho(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private static String ultimoSegmento(String caminhoPropriedade) {
        int ponto = caminhoPropriedade.lastIndexOf('.');
        return ponto >= 0 ? caminhoPropriedade.substring(ponto + 1) : caminhoPropriedade;
    }
}
