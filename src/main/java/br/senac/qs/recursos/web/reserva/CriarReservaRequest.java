package br.senac.qs.recursos.web.reserva;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CriarReservaRequest(
        @NotNull @Positive Long salaId,
        @NotNull OffsetDateTime inicio,
        @NotNull OffsetDateTime fim,
        @NotBlank @Size(max = 200) String finalidade) {
}
