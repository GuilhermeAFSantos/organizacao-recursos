package br.senac.qs.recursos.domain.pesquisa;

import java.time.Instant;

import br.senac.qs.recursos.domain.model.StatusReserva;

/**
 * Critérios da listagem de reservas. Todos opcionais (nulo = não filtra). O período pega as
 * reservas que se sobrepõem a {@code [de, ate)}, a mesma regra de sobreposição do resto do sistema.
 *
 * @param solicitanteId preenchido pelo caso de uso quando quem consulta é um SOLICITANTE
 */
public record FiltroReservas(Long solicitanteId, StatusReserva status, Instant de, Instant ate) {
}
