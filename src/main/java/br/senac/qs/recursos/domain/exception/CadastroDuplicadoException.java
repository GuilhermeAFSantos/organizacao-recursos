package br.senac.qs.recursos.domain.exception;

/** Já existe um cadastro com o mesmo identificador único (ex.: e-mail): HTTP 409. */
public class CadastroDuplicadoException extends RuntimeException {

    public CadastroDuplicadoException(String mensagemParaUsuario) {
        super(mensagemParaUsuario);
    }
}
