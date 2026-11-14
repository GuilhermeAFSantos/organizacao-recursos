package br.senac.qs.recursos.domain.exception;

/**
 * Violação de uma regra de negócio (HTTP 422).
 *
 * <p><b>Contrato:</b> a mensagem das exceções de domínio é escrita para o USUÁRIO FINAL, em
 * português, e nunca contém detalhes internos (SQL, nomes de classe, stack trace). Por isso o
 * handler global pode repassá-la com segurança no campo {@code detail} do ProblemDetail.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagemParaUsuario) {
        super(mensagemParaUsuario);
    }
}
