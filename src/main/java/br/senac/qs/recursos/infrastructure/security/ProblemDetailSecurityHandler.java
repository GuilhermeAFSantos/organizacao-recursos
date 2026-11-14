package br.senac.qs.recursos.infrastructure.security;

import java.io.IOException;
import java.time.Clock;

import br.senac.qs.recursos.web.error.TipoProblema;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Erros de autenticação (401) e autorização (403) acontecem nos filtros do Spring Security,
 * ANTES de a requisição chegar aos controllers. Por isso o {@code @RestControllerAdvice} não
 * os vê, e esta classe produz o mesmo formato ProblemDetail, com mensagens em português.
 *
 * <p>Não revela o motivo técnico da falha do token (expirado, assinatura inválida etc.),
 * para não dar pistas a um atacante.
 */
@Component
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String DETALHE_NAO_AUTENTICADO =
            "É necessário estar autenticado. Envie um token válido no cabeçalho Authorization (Bearer).";
    static final String DETALHE_ACESSO_NEGADO =
            "Seu perfil não tem permissão para realizar esta operação.";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProblemDetailSecurityHandler(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
                         AuthenticationException excecao) throws IOException {
        resposta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escrever(resposta, TipoProblema.NAO_AUTENTICADO.criar(DETALHE_NAO_AUTENTICADO, requisicao.getRequestURI(), clock));
    }

    @Override
    public void handle(HttpServletRequest requisicao, HttpServletResponse resposta,
                       AccessDeniedException excecao) throws IOException {
        escrever(resposta, TipoProblema.ACESSO_NEGADO.criar(DETALHE_ACESSO_NEGADO, requisicao.getRequestURI(), clock));
    }

    private void escrever(HttpServletResponse resposta, ProblemDetail problema) throws IOException {
        resposta.setStatus(problema.getStatus());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(resposta.getOutputStream(), problema);
    }
}
