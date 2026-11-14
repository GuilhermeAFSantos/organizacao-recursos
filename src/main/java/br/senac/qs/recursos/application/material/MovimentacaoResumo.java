package br.senac.qs.recursos.application.material;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.EstadoConservacao;
import br.senac.qs.recursos.domain.model.MovimentacaoMaterial;
import br.senac.qs.recursos.domain.model.TipoMovimentacao;

/** Uma retirada ou devolução (RF09), com quem a registrou. */
public record MovimentacaoResumo(Long id, TipoMovimentacao tipo, Long materialId, Long reservaId, Instant dataHora,
                                 EstadoConservacao estadoConservacao, String observacao, String registradoPor,
                                 boolean atrasada) {

    static MovimentacaoResumo de(MovimentacaoMaterial m) {
        return new MovimentacaoResumo(m.getId(), m.getTipo(), m.getMaterial().getId(), m.getReserva().getId(),
                m.getDataHora(), m.getEstadoConservacao(), m.getObservacao(), m.getRegistradoPor().getEmail(),
                m.isAtrasada());
    }
}
