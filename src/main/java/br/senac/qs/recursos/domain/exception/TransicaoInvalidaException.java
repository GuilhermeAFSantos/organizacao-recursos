package br.senac.qs.recursos.domain.exception;

/** Mudança de estado não permitida pela máquina de estados (RC05): HTTP 422. */
public class TransicaoInvalidaException extends RegraNegocioException {

    public TransicaoInvalidaException(String mensagemParaUsuario) {
        super(mensagemParaUsuario);
    }
}
