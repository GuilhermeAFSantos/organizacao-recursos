package br.senac.qs.recursos.domain.exception;

import java.util.List;

/**
 * O recurso já está ocupado (reserva ou bloqueio) no período pedido (RC02/RC03/RC04): HTTP 409.
 * Quando o conflito é com reservas ativas, informa quais são (ex.: ao tentar bloquear um período).
 */
public class ConflitoHorarioException extends RegraNegocioException {

    private final List<Long> reservasEmConflito;

    public ConflitoHorarioException(String mensagemParaUsuario) {
        this(mensagemParaUsuario, List.of());
    }

    public ConflitoHorarioException(String mensagemParaUsuario, List<Long> reservasEmConflito) {
        super(mensagemParaUsuario);
        this.reservasEmConflito = List.copyOf(reservasEmConflito);
    }

    public List<Long> getReservasEmConflito() {
        return reservasEmConflito;
    }
}
