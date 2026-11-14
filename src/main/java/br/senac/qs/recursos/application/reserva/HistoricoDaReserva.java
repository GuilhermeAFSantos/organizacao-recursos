package br.senac.qs.recursos.application.reserva;

import br.senac.qs.recursos.application.notificacao.ReservaMudou;
import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Destino das mudanças de uma reserva, usado por criação, transições e alteração de horário:
 * <ul>
 *   <li><b>Auditoria (RC08)</b>: cada mudança, na MESMA transação. Se a auditoria falhar, a operação
 *       inteira é desfeita.</li>
 *   <li><b>Notificação (RF11)</b>: um evento com a mudança final da operação. Quem o escuta só age
 *       DEPOIS do commit; uma operação desfeita não notifica ninguém.</li>
 * </ul>
 */
@Component
public class HistoricoDaReserva {

    private final RegistroAuditoriaRepository auditoria;
    private final ApplicationEventPublisher eventos;

    public HistoricoDaReserva(RegistroAuditoriaRepository auditoria, ApplicationEventPublisher eventos) {
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    public void registrar(Reserva reserva) {
        var mudancas = reserva.retirarMudancas();
        if (mudancas.isEmpty()) {
            return;
        }
        mudancas.forEach(m -> auditoria.save(RegistroAuditoria.daReserva(reserva.getId(), m)));
        eventos.publishEvent(new ReservaMudou(reserva.getId(), reserva.getSolicitante().getEmail(),
                reserva.getPeriodo(), mudancas.getLast()));
    }
}
