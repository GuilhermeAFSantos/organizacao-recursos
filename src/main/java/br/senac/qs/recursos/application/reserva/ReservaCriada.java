package br.senac.qs.recursos.application.reserva;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.StatusReserva;

public record ReservaCriada(Long id, Long salaId, Instant inicio, Instant fim, String finalidade,
                            StatusReserva status) {
}
