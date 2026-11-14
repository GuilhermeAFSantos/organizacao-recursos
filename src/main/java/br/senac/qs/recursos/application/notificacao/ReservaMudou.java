package br.senac.qs.recursos.application.notificacao;

import br.senac.qs.recursos.domain.model.MudancaDeEstado;
import br.senac.qs.recursos.domain.model.PeriodoReserva;

/**
 * Evento publicado quando uma operação muda a reserva (criação, transição ou alteração de horário).
 * Leva só a mudança FINAL da operação: a criação com aprovação automática gera um aviso, não dois.
 */
public record ReservaMudou(Long reservaId, String solicitante, PeriodoReserva periodo, MudancaDeEstado mudanca) {
}
