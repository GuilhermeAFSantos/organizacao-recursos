package br.senac.qs.recursos.web.reserva;

import java.time.Instant;

import br.senac.qs.recursos.application.reserva.ReservaCriada;
import br.senac.qs.recursos.domain.model.StatusReserva;

public record ReservaResponse(Long id, Long salaId, Instant inicio, Instant fim, String finalidade,
                              StatusReserva status) {

    static ReservaResponse de(ReservaCriada reserva) {
        return new ReservaResponse(reserva.id(), reserva.salaId(), reserva.inicio(), reserva.fim(),
                reserva.finalidade(), reserva.status());
    }
}
