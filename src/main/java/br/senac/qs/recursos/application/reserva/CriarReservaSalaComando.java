package br.senac.qs.recursos.application.reserva;

import java.time.Instant;

public record CriarReservaSalaComando(String emailSolicitante, Long salaId, Instant inicio, Instant fim,
                                      String finalidade) {
}
