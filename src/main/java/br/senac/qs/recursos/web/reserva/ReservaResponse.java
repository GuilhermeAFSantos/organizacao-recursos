package br.senac.qs.recursos.web.reserva;

import java.time.Instant;
import java.util.List;

import br.senac.qs.recursos.application.reserva.ReservaResumo;
import br.senac.qs.recursos.domain.model.StatusReserva;

/** Reserva devolvida pela API. {@code professorId} nulo e {@code materiaisIds} vazia quando não pedidos. */
public record ReservaResponse(Long id, Long salaId, Long professorId, List<Long> materiaisIds, Instant inicio,
                              Instant fim, String finalidade, StatusReserva status) {

    static ReservaResponse de(ReservaResumo reserva) {
        return new ReservaResponse(reserva.id(), reserva.salaId(), reserva.professorId(), reserva.materiaisIds(),
                reserva.inicio(), reserva.fim(), reserva.finalidade(), reserva.status());
    }
}
