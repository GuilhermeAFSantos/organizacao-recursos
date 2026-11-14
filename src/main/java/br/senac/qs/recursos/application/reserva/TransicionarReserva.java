package br.senac.qs.recursos.application.reserva;

import java.time.Clock;
import java.time.Instant;

import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import br.senac.qs.recursos.domain.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso das mudanças de estado da reserva (RC05). As regras ficam na {@link Reserva}; aqui
 * ficam a transação, a busca dos dados, a gravação e a auditoria (RC08).
 *
 * <p>Concorrência: duas decisões simultâneas sobre a mesma reserva (ex.: aprovar × cancelar) são
 * resolvidas pelo {@code @Version} da reserva. A segunda gravação encontra a versão desatualizada
 * e falha (HTTP 409 "Edição concorrente"); nada dela é gravado, nem a auditoria.
 */
@Service
public class TransicionarReserva {

    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;
    private final RegistroAuditoriaRepository auditoria;
    private final Clock clock;

    public TransicionarReserva(ReservaRepository reservas, UsuarioRepository usuarios,
                               RegistroAuditoriaRepository auditoria, Clock clock) {
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @FunctionalInterface
    private interface Transicao {
        void aplicar(Reserva reserva, Usuario autor, Instant agora);
    }

    @Transactional
    public ReservaCriada aprovar(Long reservaId, String emailAutor) {
        return executar(reservaId, emailAutor, Reserva::aprovar);
    }

    @Transactional
    public ReservaCriada rejeitar(Long reservaId, String emailAutor, String motivo) {
        return executar(reservaId, emailAutor, (r, autor, agora) -> r.rejeitar(autor, motivo, agora));
    }

    @Transactional
    public ReservaCriada cancelar(Long reservaId, String emailAutor, String motivo) {
        return executar(reservaId, emailAutor, (r, autor, agora) -> r.cancelar(autor, motivo, agora));
    }

    @Transactional
    public ReservaCriada iniciarUso(Long reservaId, String emailAutor) {
        return executar(reservaId, emailAutor, Reserva::iniciarUso);
    }

    @Transactional
    public ReservaCriada registrarNaoComparecimento(Long reservaId, String emailAutor, String motivo) {
        return executar(reservaId, emailAutor, (r, autor, agora) -> r.registrarNaoComparecimento(autor, motivo, agora));
    }

    @Transactional
    public ReservaCriada concluir(Long reservaId, String emailAutor) {
        return executar(reservaId, emailAutor, Reserva::concluir);
    }

    private ReservaCriada executar(Long reservaId, String emailAutor, Transicao transicao) {
        var reserva = reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva " + reservaId + " não encontrada."));
        var autor = usuarios.findByEmailIgnoreCase(emailAutor)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new AccessDeniedException("usuário do token inexistente ou inativo"));

        transicao.aplicar(reserva, autor, clock.instant());

        // Flush agora: o UPDATE com checagem de versão roda aqui, não no commit.
        reservas.saveAndFlush(reserva);
        reserva.retirarMudancas().forEach(m -> auditoria.save(RegistroAuditoria.daReserva(reserva.getId(), m)));
        var periodo = reserva.getPeriodo();
        return new ReservaCriada(reserva.getId(), reserva.getOcupacoes().get(0).getRecurso().getId(),
                periodo.inicio(), periodo.fim(), reserva.getFinalidade(), reserva.getStatus());
    }
}
