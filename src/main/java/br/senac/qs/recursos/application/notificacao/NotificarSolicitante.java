package br.senac.qs.recursos.application.notificacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa quem reservou a cada mudança (RF11), DEPOIS do commit:
 * <ul>
 *   <li>a reserva já está gravada, então uma falha do serviço externo não a desfaz;</li>
 *   <li>operação recusada (rollback) não chega aqui, então ninguém é avisado de algo que não houve.</li>
 * </ul>
 * A falha é registrada no log e engolida: a operação do usuário já deu certo.
 */
@Component
public class NotificarSolicitante {

    private static final Logger log = LoggerFactory.getLogger(NotificarSolicitante.class);

    private final Notificador notificador;

    public NotificarSolicitante(Notificador notificador) {
        this.notificador = notificador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoMudarReserva(ReservaMudou evento) {
        var notificacao = Notificacao.de(evento);
        try {
            notificador.enviar(notificacao);
        } catch (RuntimeException e) {
            log.warn("Notificação {} da reserva {} não enviada: {}", notificacao.evento(), notificacao.reservaId(),
                    e.getMessage());
        }
    }
}
