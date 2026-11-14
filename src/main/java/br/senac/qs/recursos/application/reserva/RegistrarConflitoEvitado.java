package br.senac.qs.recursos.application.reserva;

import java.time.Clock;
import java.util.function.Supplier;

import br.senac.qs.recursos.domain.model.ConflitoEvitado;
import br.senac.qs.recursos.domain.repository.ConflitoEvitadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executa uma operação de reserva na sua própria transação e, se ela for recusada por sobreposição,
 * grava o conflito evitado (RF12) DEPOIS, em outra transação.
 *
 * <p>Por que depois, e não dentro: dentro da transação da reserva, o registro seria desfeito no
 * rollback. E gravar "no meio" (REQUIRES_NEW, ou num listener de rollback) exige uma SEGUNDA conexão
 * do pool enquanto a primeira ainda está presa; com 20 requisições simultâneas e 10 conexões, todas
 * esperavam por todas até o tempo limite (o {@code ReservaConcorrenciaIT} mostrou isso). Aqui a
 * transação da reserva já terminou e devolveu a conexão quando o registro começa.
 */
@Component
public class RegistrarConflitoEvitado {

    private static final Logger log = LoggerFactory.getLogger(RegistrarConflitoEvitado.class);

    private final TransactionTemplate transacao;
    private final ConflitoEvitadoRepository conflitos;
    private final Clock clock;

    public RegistrarConflitoEvitado(PlatformTransactionManager transacoes, ConflitoEvitadoRepository conflitos,
                                    Clock clock) {
        this.transacao = new TransactionTemplate(transacoes);
        this.conflitos = conflitos;
        this.clock = clock;
    }

    public <T> T executarRegistrandoConflito(Supplier<T> operacao) {
        try {
            return transacao.execute(status -> operacao.get());
        } catch (ConflitoEvitadoException conflito) {
            registrar(conflito);
            throw conflito;
        }
    }

    /** Falhar aqui não muda a resposta ao usuário (409): só fica no log. */
    private void registrar(ConflitoEvitadoException c) {
        try {
            transacao.executeWithoutResult(status -> conflitos.save(ConflitoEvitado.registrar(c.getRecursoId(),
                    c.getSolicitanteId(), c.getPeriodo(), c.getOrigem(), clock.instant())));
        } catch (RuntimeException e) {
            log.warn("Conflito evitado não registrado: {}", e.getMessage());
        }
    }
}
