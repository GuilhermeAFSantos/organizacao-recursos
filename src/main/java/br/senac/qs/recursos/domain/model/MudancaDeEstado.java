package br.senac.qs.recursos.domain.model;

import java.time.Instant;

/**
 * Uma mudança de estado da reserva, com tudo o que a auditoria exige (RC08): quem, quando, estado
 * anterior, estado novo e motivo. {@code anterior} é nulo na criação. {@code autorId} é nulo quando
 * o autor é o próprio sistema (aprovação automática).
 */
public record MudancaDeEstado(StatusReserva anterior, StatusReserva novo, Long autorId, String autorEmail,
                              String motivo, Instant quando) {

    public static final String AUTOR_SISTEMA = "sistema";
}
