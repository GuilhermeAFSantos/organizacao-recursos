package br.senac.qs.recursos.domain.model;

import java.time.Instant;

import br.senac.qs.recursos.domain.exception.RegraNegocioException;

public record PeriodoReserva(Instant inicio, Instant fim) {

    public PeriodoReserva {
        if (inicio == null) {
            throw new RegraNegocioException("O início da reserva é obrigatório.");
        }
        if (fim == null) {
            throw new RegraNegocioException("O término da reserva é obrigatório.");
        }
        if (fim.compareTo(inicio) <= 0) {
            throw new RegraNegocioException("O término da reserva deve ser posterior ao início.");
        }
    }
}
