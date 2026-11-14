package br.senac.qs.recursos.web.reserva;

import java.time.OffsetDateTime;

import br.senac.qs.recursos.web.validacao.MinutoCheio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CriarReservaRequest(
        @NotNull @Positive Long salaId,
        @NotNull @MinutoCheio OffsetDateTime inicio,
        @NotNull @MinutoCheio OffsetDateTime fim,
        @NotBlank @Size(max = 200) String finalidade) {
}
