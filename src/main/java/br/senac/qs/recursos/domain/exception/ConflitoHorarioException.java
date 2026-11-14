package br.senac.qs.recursos.domain.exception;

/** O recurso já está ocupado (reserva ou manutenção) no período pedido (RC02/RC03/RC04): HTTP 409. */
public class ConflitoHorarioException extends RegraNegocioException {

    public ConflitoHorarioException(String mensagemParaUsuario) {
        super(mensagemParaUsuario);
    }
}
