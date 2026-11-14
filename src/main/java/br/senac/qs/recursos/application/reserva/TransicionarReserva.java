package br.senac.qs.recursos.application.reserva;

import java.time.Clock;
import java.time.Instant;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.model.Usuario;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
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
    private final UsuarioDoToken usuarioDoToken;
    private final HistoricoDaReserva historico;
    private final Clock clock;

    public TransicionarReserva(ReservaRepository reservas, UsuarioDoToken usuarioDoToken,
                               HistoricoDaReserva historico, Clock clock) {
        this.reservas = reservas;
        this.usuarioDoToken = usuarioDoToken;
        this.historico = historico;
        this.clock = clock;
    }

    @FunctionalInterface
    private interface Transicao {
        void aplicar(Reserva reserva, Usuario autor, Instant agora);
    }

    @Transactional
    public ReservaResumo aprovar(Long reservaId, String emailAutor) {
        return executar(reservaId, emailAutor, Reserva::aprovar);
    }

    @Transactional
    public ReservaResumo rejeitar(Long reservaId, String emailAutor, String motivo) {
        return executar(reservaId, emailAutor, (r, autor, agora) -> r.rejeitar(autor, motivo, agora));
    }

    @Transactional
    public ReservaResumo cancelar(Long reservaId, String emailAutor, String motivo) {
        return executar(reservaId, emailAutor, (r, autor, agora) -> r.cancelar(autor, motivo, agora));
    }

    @Transactional
    public ReservaResumo iniciarUso(Long reservaId, String emailAutor) {
        return executar(reservaId, emailAutor, Reserva::iniciarUso);
    }

    @Transactional
    public ReservaResumo registrarNaoComparecimento(Long reservaId, String emailAutor, String motivo) {
        return executar(reservaId, emailAutor, (r, autor, agora) -> r.registrarNaoComparecimento(autor, motivo, agora));
    }

    @Transactional
    public ReservaResumo concluir(Long reservaId, String emailAutor) {
        return executar(reservaId, emailAutor, Reserva::concluir);
    }

    private ReservaResumo executar(Long reservaId, String emailAutor, Transicao transicao) {
        var reserva = reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva " + reservaId + " não encontrada."));
        var autor = usuarioDoToken.ativo(emailAutor);

        transicao.aplicar(reserva, autor, clock.instant());

        // Flush agora: o UPDATE com checagem de versão roda aqui, não no commit.
        reservas.saveAndFlush(reserva);
        historico.registrar(reserva);
        return ReservaResumo.de(reserva);
    }
}
