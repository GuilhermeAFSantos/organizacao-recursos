package br.senac.qs.recursos.web.reserva;

import java.time.Instant;
import java.util.List;

import br.senac.qs.recursos.application.reserva.ReservaResumo;
import br.senac.qs.recursos.application.reserva.ReservaResumo.RecursoDaReserva;
import br.senac.qs.recursos.domain.model.StatusReserva;

/**
 * Reserva devolvida pela API. {@code professorId} nulo e {@code materiaisIds} vazia quando não pedidos;
 * {@code recursos} traz todos pelo nome, para a interface exibir sem outra consulta.
 */
public record ReservaResponse(Long id, Long salaId, Long professorId, List<Long> materiaisIds, Instant inicio,
                              Instant fim, String finalidade, StatusReserva status, String solicitante, String motivo,
                              List<RecursoDaReserva> recursos) {

    static ReservaResponse de(ReservaResumo r) {
        return new ReservaResponse(r.id(), r.salaId(), r.professorId(), r.materiaisIds(), r.inicio(), r.fim(),
                r.finalidade(), r.status(), r.solicitante(), r.motivo(), r.recursos());
    }
}
