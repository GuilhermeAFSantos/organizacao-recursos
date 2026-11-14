package br.senac.qs.recursos.application.bloqueio;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.TipoBloqueio;

public record RegistrarBloqueioComando(String emailAutor, Long recursoId, TipoBloqueio tipo, Instant inicio,
                                       Instant fim, String motivo) {
}
