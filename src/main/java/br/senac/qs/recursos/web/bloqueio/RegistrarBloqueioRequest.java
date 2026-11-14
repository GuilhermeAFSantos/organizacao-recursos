package br.senac.qs.recursos.web.bloqueio;

import java.time.OffsetDateTime;

import br.senac.qs.recursos.domain.model.TipoBloqueio;
import br.senac.qs.recursos.web.validacao.MinutoCheio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistrarBloqueioRequest(
        @NotNull @Positive Long recursoId,
        @NotNull TipoBloqueio tipo,
        @NotBlank @Size(max = 500) String motivo,
        @NotNull @MinutoCheio OffsetDateTime inicio,
        @NotNull @MinutoCheio OffsetDateTime fim) {
}
