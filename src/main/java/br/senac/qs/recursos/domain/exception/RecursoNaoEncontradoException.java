package br.senac.qs.recursos.domain.exception;

/** Entidade pedida não existe (HTTP 404). A mensagem segue o contrato de {@link RegraNegocioException}. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagemParaUsuario) {
        super(mensagemParaUsuario);
    }
}
