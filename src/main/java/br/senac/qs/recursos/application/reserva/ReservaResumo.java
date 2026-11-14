package br.senac.qs.recursos.application.reserva;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.model.StatusReserva;

/** Visão da reserva devolvida pelos casos de uso (sem expor a entidade fora da transação). */
public record ReservaResumo(Long id, Long salaId, Instant inicio, Instant fim, String finalidade,
                            StatusReserva status) {

    /** Monta o resumo DENTRO da transação (lê a ocupação, que é carregada sob demanda). */
    static ReservaResumo de(Reserva reserva) {
        var periodo = reserva.getPeriodo();
        return new ReservaResumo(reserva.getId(), reserva.getOcupacoes().get(0).getRecurso().getId(),
                periodo.inicio(), periodo.fim(), reserva.getFinalidade(), reserva.getStatus());
    }
}
