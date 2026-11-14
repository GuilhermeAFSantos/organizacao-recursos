package br.senac.qs.recursos.domain.exception;

/**
 * O usuário não pode executar esta operação sobre ESTE recurso (ex.: aprovar a própria reserva,
 * cancelar a reserva de outra pessoa): HTTP 403. A mensagem segue o contrato de
 * {@link RegraNegocioException}: escrita para o usuário final, sem detalhes internos.
 */
public class OperacaoNaoPermitidaException extends RuntimeException {

    public OperacaoNaoPermitidaException(String mensagemParaUsuario) {
        super(mensagemParaUsuario);
    }
}
