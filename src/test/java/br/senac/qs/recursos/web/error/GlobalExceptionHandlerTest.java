package br.senac.qs.recursos.web.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.senac.qs.recursos.infrastructure.config.ClockConfig;
import br.senac.qs.recursos.infrastructure.security.JwtConfig;
import br.senac.qs.recursos.infrastructure.security.ProblemDetailSecurityHandler;
import br.senac.qs.recursos.infrastructure.security.SecurityConfig;
import br.senac.qs.suportetestes.ControladorDeErrosParaTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Teste de caixa-preta do contrato de erros da API: para cada tipo de falha, verifica status,
 * formato ProblemDetail (RFC 9457), mensagens em português e AUSÊNCIA de detalhes internos.
 *
 * <p>Usa a configuração de segurança real (filtros + JWT), não um mock dela.
 */
// Carrega SÓ o controller de teste: os controllers reais dependem de casos de uso e banco,
// que não fazem parte deste teste do contrato de erros.
@WebMvcTest(controllers = ControladorDeErrosParaTeste.class)
@ActiveProfiles("test")
@Import({ControladorDeErrosParaTeste.class, SecurityConfig.class, JwtConfig.class,
        ProblemDetailSecurityHandler.class, ClockConfig.class})
@DisplayName("Tratamento global de erros (ProblemDetail)")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor usuario(String perfil) {
        return jwt().jwt(j -> j.subject("usuario@senac.br").claim(SecurityConfig.CLAIM_PERFIL, perfil))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + perfil));
    }

    private static MockHttpServletRequestBuilder getAutenticado(String url) {
        return get(url).with(usuario("SOLICITANTE"));
    }

    @Nested
    @DisplayName("Exceções de domínio")
    class Dominio {

        @Test
        @DisplayName("regra de negócio -> 422 com a mensagem do domínio")
        void regraNegocio() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/regra-negocio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemas/regra-negocio"))
                .andExpect(jsonPath("$.title").value("Regra de negócio violada"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Reservas iniciadas não podem ser canceladas."))
                .andExpect(jsonPath("$.instance").value("/teste-erros/regra-negocio"))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
        }

        @Test
        @DisplayName("conflito de horário -> 409 (tipo específico, não o genérico de regra de negócio)")
        void conflito() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/conflito"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problemas/conflito-horario"))
                .andExpect(jsonPath("$.title").value("Conflito de horário"))
                .andExpect(jsonPath("$.detail").value("A sala 101 já está reservada nesse horário."));
        }

        @Test
        @DisplayName("entidade inexistente -> 404")
        void naoEncontrado() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/nao-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Não encontrado"))
                .andExpect(jsonPath("$.detail").value("Sala 999 não encontrada."));
        }
    }

    @Nested
    @DisplayName("Validação de entrada")
    class Validacao {

        @Test
        @DisplayName("corpo com campos inválidos -> 400 listando cada campo em português")
        void camposInvalidos() throws Exception {
            mvc.perform(post("/teste-erros/sala").with(usuario("SOLICITANTE"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nome\": \"\", \"capacidade\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problemas/dados-invalidos"))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.DETALHE_DADOS_INVALIDOS))
                .andExpect(jsonPath("$.erros.length()").value(2))
                .andExpect(jsonPath("$.erros[0].campo").value("capacidade"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("deve ser maior que 0"))
                .andExpect(jsonPath("$.erros[1].campo").value("nome"))
                .andExpect(jsonPath("$.erros[1].mensagem").value("não deve estar em branco"));
        }

        @Test
        @DisplayName("violação de restrição fora do corpo HTTP -> 400 com a mesma estrutura de erros")
        void restricaoViolada() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/restricao"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problemas/dados-invalidos"))
                .andExpect(jsonPath("$.erros.length()").value(2))
                .andExpect(jsonPath("$.erros[0].campo").value("capacidade"))
                .andExpect(jsonPath("$.erros[1].campo").value("nome"));
        }

        @Test
        @DisplayName("JSON malformado -> 400 com orientação, sem a mensagem do parser")
        void jsonMalformado() throws Exception {
            mvc.perform(post("/teste-erros/sala").with(usuario("SOLICITANTE"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nome\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.DETALHE_CORPO_ILEGIVEL))
                .andExpect(content().string(not(containsString("JsonParseException"))))
                .andExpect(content().string(not(containsString("Unexpected end-of-input"))));
        }

        @Test
        @DisplayName("campo desconhecido no JSON é rejeitado (proteção contra mass assignment)")
        void campoDesconhecido() throws Exception {
            mvc.perform(post("/teste-erros/sala").with(usuario("SOLICITANTE"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nome\": \"Lab 1\", \"capacidade\": 30, \"status\": \"APROVADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.DETALHE_CORPO_ILEGIVEL));
        }

        @Test
        @DisplayName("parâmetro com tipo errado -> 400 citando o parâmetro")
        void tipoErrado() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/parametro").param("numero", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O parâmetro 'numero' recebeu um valor em formato inválido."));
        }

        @Test
        @DisplayName("parâmetro obrigatório ausente -> 400 citando o parâmetro")
        void parametroAusente() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O parâmetro obrigatório 'numero' não foi informado."));
        }

        @Test
        @DisplayName("parâmetro fora da faixa permitida -> 400")
        void parametroForaDaFaixa() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/parametro-validado").param("quantidade", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Um ou mais parâmetros da requisição são inválidos."));
        }
    }

    @Nested
    @DisplayName("Erros de protocolo HTTP")
    class Protocolo {

        @Test
        @DisplayName("método não suportado -> 405 em português, com cabeçalho Allow")
        void metodoNaoSuportado() throws Exception {
            mvc.perform(post("/teste-erros/regra-negocio").with(usuario("SOLICITANTE")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.title").value("Método não suportado"))
                .andExpect(jsonPath("$.detail").value("O método POST não é suportado neste endereço."));
        }

        @Test
        @DisplayName("formato de conteúdo não suportado -> 415")
        void formatoNaoSuportado() throws Exception {
            mvc.perform(post("/teste-erros/sala").with(usuario("SOLICITANTE"))
                    .contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Formato não suportado"));
        }

        @Test
        @DisplayName("endereço inexistente -> 404 em português")
        void enderecoInexistente() throws Exception {
            mvc.perform(getAutenticado("/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Não encontrado"))
                .andExpect(jsonPath("$.detail").value("O endereço solicitado não existe."));
        }
    }

    @Nested
    @DisplayName("Persistência")
    class Persistencia {

        @Test
        @DisplayName("edição concorrente (lock otimista) -> 409 orientando a recarregar")
        void edicaoConcorrente() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/concorrencia"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problemas/edicao-concorrente"))
                .andExpect(content().string(not(containsString("br.senac"))));
        }

        @Test
        @DisplayName("violação de integridade -> 409 SEM expor SQL nem nome de constraint")
        void violacaoIntegridade() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/integridade"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problemas/violacao-integridade"))
                .andExpect(content().string(not(containsString("uk_usuario_email"))))
                .andExpect(content().string(not(containsString("SQL"))));
        }
    }

    @Nested
    @DisplayName("Erro inesperado")
    class Inesperado {

        @Test
        @DisplayName("-> 500 genérico com código de rastreio, sem stack trace nem mensagem interna")
        void erroInterno() throws Exception {
            mvc.perform(getAutenticado("/teste-erros/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("/problemas/erro-interno"))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.codigoErro").value(notNullValue()))
                .andExpect(jsonPath("$.detail").value(containsString("informe o código")))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("ClasseInterna"))))
                .andExpect(content().string(not(containsString("at br."))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Autenticação e autorização")
    class Seguranca {

        @Test
        @DisplayName("sem token -> 401 ProblemDetail com WWW-Authenticate: Bearer")
        void semToken() throws Exception {
            mvc.perform(get("/teste-erros/regra-negocio"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemas/nao-autenticado"))
                .andExpect(jsonPath("$.title").value("Não autenticado"));
        }

        @Test
        @DisplayName("token inválido -> 401 sem revelar o motivo técnico da rejeição")
        void tokenInvalido() throws Exception {
            mvc.perform(get("/teste-erros/regra-negocio").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(content().string(not(containsString("Malformed"))))
                .andExpect(content().string(not(containsString("Jwt"))));
        }

        @Test
        @DisplayName("perfil sem permissão -> 403 (e não 500)")
        void perfilSemPermissao() throws Exception {
            mvc.perform(get("/teste-erros/somente-admin").with(usuario("SOLICITANTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/problemas/acesso-negado"))
                .andExpect(jsonPath("$.detail").value("Seu perfil não tem permissão para realizar esta operação."));
        }

        @Test
        @DisplayName("perfil com permissão -> 200")
        void perfilComPermissao() throws Exception {
            mvc.perform(get("/teste-erros/somente-admin").with(usuario("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
        }
    }
}
