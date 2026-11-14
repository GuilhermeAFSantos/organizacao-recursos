package br.senac.qs.recursos.web.reserva;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo das ações que EXIGEM motivo (rejeição). */
public record MotivoRequest(@NotBlank @Size(max = 500) String motivo) {
}
