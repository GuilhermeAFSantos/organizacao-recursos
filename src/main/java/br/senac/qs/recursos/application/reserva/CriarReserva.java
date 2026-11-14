package br.senac.qs.recursos.application.reserva;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.application.ViolacaoDeExclusao;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.repository.MaterialRepository;
import br.senac.qs.recursos.domain.repository.ProfessorRepository;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import br.senac.qs.recursos.domain.repository.SalaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: um SOLICITANTE reserva uma sala, opcionalmente com professor e materiais (RF04, RF05).
 * Tudo ou nada: ou todos os recursos ficam reservados, ou nenhum.
 *
 * <p>Ordem: RC01 (período válido) → solicitante ativo → recursos TRAVADOS em ordem de id → cada
 * recurso existe (404) e é reservável (422) → todos livres (409) → grava → audita.
 *
 * <p>Concorrência (RC03): lock dos recursos ({@link AgendaDeRecursos}) e, como garantia final, a
 * constraint de exclusão do banco, cuja recusa (23P01) também vira 409.
 */
@Service
public class CriarReserva {

    private final UsuarioDoToken usuarioDoToken;
    private final AgendaDeRecursos agenda;
    private final SalaRepository salas;
    private final ProfessorRepository professores;
    private final MaterialRepository materiais;
    private final ReservaRepository reservas;
    private final HistoricoDaReserva historico;
    private final Clock clock;

    @SuppressWarnings("java:S107") // um repositório por tipo de recurso: juntar esconderia de onde vem cada um
    public CriarReserva(UsuarioDoToken usuarioDoToken, AgendaDeRecursos agenda, SalaRepository salas,
                        ProfessorRepository professores, MaterialRepository materiais, ReservaRepository reservas,
                        HistoricoDaReserva historico, Clock clock) {
        this.usuarioDoToken = usuarioDoToken;
        this.agenda = agenda;
        this.salas = salas;
        this.professores = professores;
        this.materiais = materiais;
        this.reservas = reservas;
        this.historico = historico;
        this.clock = clock;
    }

    @Transactional
    public ReservaResumo executar(CriarReservaComando comando) {
        var periodo = new PeriodoReserva(comando.inicio(), comando.fim());
        var solicitante = usuarioDoToken.ativo(comando.emailSolicitante());
        agenda.travarEmOrdem(idsDosRecursos(comando));
        var recursos = carregar(comando);

        var reserva = Reserva.solicitar(solicitante, recursos, periodo, comando.finalidade(), clock.instant());
        agenda.garantirLivre(recursos, periodo, null);
        var gravada = gravar(reserva, recursos);
        historico.registrar(gravada);
        return ReservaResumo.de(gravada);
    }

    private static List<Long> idsDosRecursos(CriarReservaComando comando) {
        var ids = new ArrayList<Long>();
        ids.add(comando.salaId());
        if (comando.professorId() != null) {
            ids.add(comando.professorId());
        }
        ids.addAll(comando.materiaisIds());
        return ids;
    }

    /** Cada id precisa existir e ser do tipo pedido: uma sala informada como professor é 404. */
    private List<Recurso> carregar(CriarReservaComando comando) {
        var recursos = new ArrayList<Recurso>();
        Long salaId = comando.salaId();
        recursos.add(salas.findById(salaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala " + salaId + " não encontrada.")));
        Long professorId = comando.professorId();
        if (professorId != null) {
            recursos.add(professores.findById(professorId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Professor " + professorId + " não encontrado.")));
        }
        for (Long materialId : comando.materiaisIds()) {
            recursos.add(materiais.findById(materialId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material " + materialId + " não encontrado.")));
        }
        return recursos;
    }

    /**
     * Barreira definitiva: a constraint de exclusão. {@code saveAndFlush} força o INSERT agora, para
     * a recusa (23P01) surgir aqui, onde ainda dá para traduzi-la em mensagem de negócio.
     */
    private Reserva gravar(Reserva reserva, List<Recurso> recursos) {
        try {
            return reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            if (ViolacaoDeExclusao.causou(e)) {
                throw agenda.conflitoNoBanco(recursos);
            }
            throw e; // outra violação (FK, CHECK...) não é conflito de horário: não mascarar
        }
    }
}
