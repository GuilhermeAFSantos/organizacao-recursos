package br.senac.qs.recursos.application.reserva;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.application.ViolacaoDeExclusao;
import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import br.senac.qs.recursos.domain.model.Ocupacao;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.model.Sala;
import br.senac.qs.recursos.domain.model.TipoBloqueio;
import br.senac.qs.recursos.domain.repository.OcupacaoRepository;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import br.senac.qs.recursos.domain.repository.SalaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: um SOLICITANTE reserva uma sala (RF04).
 *
 * <p>Ordem das verificações: RC01 (período válido) → solicitante ativo → recurso TRAVADO → sala
 * existente e ativa → horário livre (RC02 contra reservas, RC04 contra bloqueios) → grava.
 *
 * <p>Concorrência (RC03), em camadas:
 * <ol>
 *   <li><b>Lock do recurso</b> ({@link RecursoRepository#travar}), o mesmo usado pelos bloqueios:
 *       serializa tudo que ocupa o recurso; a pré-checagem vê o que acabou de ser confirmado.</li>
 *   <li><b>Constraint de exclusão</b>: garantia final, mesmo para código que esqueça o lock.
 *       Sua recusa (23P01) também vira 409.</li>
 * </ol>
 */
@Service
public class CriarReservaSala {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UsuarioDoToken usuarioDoToken;
    private final RecursoRepository recursos;
    private final SalaRepository salas;
    private final OcupacaoRepository ocupacoes;
    private final ReservaRepository reservas;
    private final RegistroAuditoriaRepository auditoria;
    private final Clock clock;

    public CriarReservaSala(UsuarioDoToken usuarioDoToken, RecursoRepository recursos, SalaRepository salas,
                            OcupacaoRepository ocupacoes, ReservaRepository reservas,
                            RegistroAuditoriaRepository auditoria, Clock clock) {
        this.usuarioDoToken = usuarioDoToken;
        this.recursos = recursos;
        this.salas = salas;
        this.ocupacoes = ocupacoes;
        this.reservas = reservas;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public ReservaResumo executar(CriarReservaSalaComando comando) {
        var periodo = new PeriodoReserva(comando.inicio(), comando.fim());
        var solicitante = usuarioDoToken.ativo(comando.emailSolicitante());
        var sala = salaAtivaTravada(comando.salaId());
        garantirHorarioLivre(sala, periodo);

        var reserva = gravar(Reserva.solicitar(solicitante, sala, periodo, comando.finalidade(), clock.instant()), sala);
        // RC08: na mesma transação da reserva. Se a auditoria falhar, a reserva também é desfeita.
        reserva.retirarMudancas().forEach(m -> auditoria.save(RegistroAuditoria.daReserva(reserva.getId(), m)));
        return ReservaResumo.de(reserva);
    }

    /** Trava a sala; se ela existe mas foi desativada, a recusa diz isso em vez de "não encontrada". */
    private Sala salaAtivaTravada(Long salaId) {
        var travada = recursos.travar(salaId).isPresent();
        var sala = salas.findById(salaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala " + salaId + " não encontrada."));
        if (!travada) {
            throw new RegraNegocioException("A sala " + sala.getNome() + " está desativada e não aceita reservas.");
        }
        return sala;
    }

    /**
     * RC02/RC04 na aplicação: mensagem clara ao usuário, dizendo se o conflito é com outra reserva ou
     * com um bloqueio. Só é confiável sob concorrência porque o recurso já está travado.
     */
    private void garantirHorarioLivre(Sala sala, PeriodoReserva periodo) {
        List<Ocupacao> conflitos = ocupacoes.sobrepostas(sala.getId(), periodo.inicio(), periodo.fim());
        if (conflitos.isEmpty()) {
            return;
        }
        var bloqueio = conflitos.stream().map(Ocupacao::getBloqueio).filter(Objects::nonNull).findFirst();
        if (bloqueio.isPresent()) {
            var p = bloqueio.get().getPeriodo();
            String situacao = bloqueio.get().getTipo() == TipoBloqueio.MANUTENCAO ? "está em manutenção" : "está bloqueada";
            throw new ConflitoHorarioException("A sala " + sala.getNome() + " " + situacao + " das "
                    + HORA.format(p.inicio().atZone(BRASILIA)) + " às " + HORA.format(p.fim().atZone(BRASILIA))
                    + " de " + DATA.format(p.inicio().atZone(BRASILIA)) + ". Escolha outro horário ou outra sala.");
        }
        throw conflito(sala);
    }

    /**
     * Barreira definitiva: a constraint de exclusão. {@code saveAndFlush} força o INSERT agora, para
     * a recusa (23P01) surgir aqui, onde ainda dá para traduzi-la em mensagem de negócio.
     */
    private Reserva gravar(Reserva reserva, Sala sala) {
        try {
            return reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            if (ViolacaoDeExclusao.causou(e)) {
                throw conflito(sala);
            }
            throw e; // outra violação (FK, CHECK...) não é conflito de horário: não mascarar
        }
    }

    private static ConflitoHorarioException conflito(Sala sala) {
        return new ConflitoHorarioException("A sala " + sala.getNome()
                + " já está ocupada nesse horário. Escolha outro horário ou outra sala.");
    }
}
