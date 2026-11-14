package br.senac.qs.recursos.web.reserva;

import java.time.OffsetDateTime;

import br.senac.qs.recursos.web.validacao.MinutoCheio;
import jakarta.validation.constraints.NotNull;

/** Novo horário da reserva (RF04). Os recursos continuam os mesmos. */
public record AlterarHorarioRequest(
        @NotNull @MinutoCheio OffsetDateTime inicio,
        @NotNull @MinutoCheio OffsetDateTime fim) {
}
