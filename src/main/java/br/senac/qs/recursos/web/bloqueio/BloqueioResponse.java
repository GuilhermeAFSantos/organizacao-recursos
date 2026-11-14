package br.senac.qs.recursos.web.bloqueio;

import java.time.Instant;

import br.senac.qs.recursos.application.bloqueio.BloqueioResumo;
import br.senac.qs.recursos.domain.model.TipoBloqueio;

public record BloqueioResponse(Long id, Long recursoId, TipoBloqueio tipo, String motivo, Instant inicio,
                               Instant fim, boolean ativo) {

    static BloqueioResponse de(BloqueioResumo bloqueio) {
        return new BloqueioResponse(bloqueio.id(), bloqueio.recursoId(), bloqueio.tipo(), bloqueio.motivo(),
                bloqueio.inicio(), bloqueio.fim(), bloqueio.ativo());
    }
}
