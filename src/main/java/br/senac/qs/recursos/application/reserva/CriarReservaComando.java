package br.senac.qs.recursos.application.reserva;

import java.time.Instant;
import java.util.List;

/** Pedido de reserva: a sala é obrigatória; professor e materiais são opcionais. */
public record CriarReservaComando(String emailSolicitante, Long salaId, Long professorId, List<Long> materiaisIds,
                                  Instant inicio, Instant fim, String finalidade) {

    public CriarReservaComando {
        materiaisIds = materiaisIds == null ? List.of() : List.copyOf(materiaisIds);
    }
}
