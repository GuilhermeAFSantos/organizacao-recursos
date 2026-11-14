package br.senac.qs.recursos.application.reserva;

import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.model.OrigemConflito;
import br.senac.qs.recursos.domain.model.PeriodoReserva;

/**
 * Recusa por sobreposição (409) que carrega o necessário para registrar o conflito evitado (RF12):
 * qual recurso (nulo se o banco não disse), quem tentou, o horário pedido e quem barrou.
 */
public class ConflitoEvitadoException extends ConflitoHorarioException {

    private final transient Long recursoId;
    private final transient Long solicitanteId;
    private final transient PeriodoReserva periodo;
    private final OrigemConflito origem;

    public ConflitoEvitadoException(String mensagemParaUsuario, Long recursoId, Long solicitanteId,
                                    PeriodoReserva periodo, OrigemConflito origem) {
        super(mensagemParaUsuario);
        this.recursoId = recursoId;
        this.solicitanteId = solicitanteId;
        this.periodo = periodo;
        this.origem = origem;
    }

    public Long getRecursoId() {
        return recursoId;
    }

    public Long getSolicitanteId() {
        return solicitanteId;
    }

    public PeriodoReserva getPeriodo() {
        return periodo;
    }

    public OrigemConflito getOrigem() {
        return origem;
    }
}
