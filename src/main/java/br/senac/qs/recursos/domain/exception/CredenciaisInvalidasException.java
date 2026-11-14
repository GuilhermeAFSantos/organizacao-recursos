package br.senac.qs.recursos.domain.exception;

/**
 * Login recusado (HTTP 401). A mensagem é SEMPRE a mesma, seja e-mail inexistente, senha errada ou
 * usuário inativo, para não revelar quais e-mails estão cadastrados.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public static final String MENSAGEM = "E-mail ou senha inválidos.";

    public CredenciaisInvalidasException() {
        super(MENSAGEM);
    }
}
