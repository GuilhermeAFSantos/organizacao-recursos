package br.senac.qs.recursos.application.material;

import java.time.Instant;

/**
 * Item retirado e ainda não devolvido: com quem está ({@code solicitante} da reserva) e até quando
 * deveria voltar ({@code devolverAte}, o fim da reserva).
 */
public record PendenciaResumo(Long materialId, String material, Long reservaId, String solicitante,
                              Instant retiradoEm, Instant devolverAte, boolean atrasada) {
}
