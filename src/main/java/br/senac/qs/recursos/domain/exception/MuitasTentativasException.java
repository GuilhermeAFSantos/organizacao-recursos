package br.senac.qs.recursos.domain.exception;

/** Usuário temporariamente bloqueado por excesso de senhas erradas (HTTP 429). */
public class MuitasTentativasException extends RuntimeException {

    public static final String MENSAGEM = "Muitas tentativas de login. Tente novamente em alguns minutos.";

    public MuitasTentativasException() {
        super(MENSAGEM);
    }
}
