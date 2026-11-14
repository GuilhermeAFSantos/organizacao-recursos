package br.senac.qs.recursos.application.bloqueio;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.Bloqueio;
import br.senac.qs.recursos.domain.model.TipoBloqueio;

public record BloqueioResumo(Long id, Long recursoId, TipoBloqueio tipo, String motivo, Instant inicio,
                             Instant fim, boolean ativo) {

    static BloqueioResumo de(Bloqueio bloqueio) {
        var periodo = bloqueio.getPeriodo();
        return new BloqueioResumo(bloqueio.getId(), bloqueio.getOcupacoes().get(0).getRecurso().getId(),
                bloqueio.getTipo(), bloqueio.getMotivo(), periodo.inicio(), periodo.fim(), bloqueio.isAtivo());
    }
}
