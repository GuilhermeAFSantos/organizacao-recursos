package br.senac.qs.recursos.application.notificacao;

import java.time.Instant;

/** O que é enviado ao serviço externo de notificação (RF11). */
public record Notificacao(String evento, Long reservaId, String destinatario, String mensagem, Instant ocorridoEm) {

    public static Notificacao de(ReservaMudou evento) {
        throw new UnsupportedOperationException("a implementar");
    }
}
