package br.senac.qs.recursos.application.reserva;

import java.time.Clock;
import java.time.Instant;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.application.ViolacaoDeExclusao;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.model.Ocupacao;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import br.senac.qs.recursos.domain.repository.ReservaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: mudar o horário de uma reserva (RF04), mantendo os mesmos recursos.
 *
 * <p>Ordem: RC01 (novo período válido) → reserva existe → autor ativo → recursos TRAVADOS em ordem
 * → alteração permitida (quem, estado, horário; sem mudar nada ainda) → todos os recursos livres
 * no novo período, ignorando a própria reserva → altera reserva e ocupações juntas → grava → audita.
 *
 * <p>Concorrência: o lock dos recursos serializa a alteração com outras reservas e bloqueios; o
 * {@code @Version} da reserva recusa (409) uma alteração feita sobre uma versão já mudada por outra
 * requisição; a constraint de exclusão continua sendo a garantia final.
 */
@Service
public class AlterarHorarioReserva {

    private final ReservaRepository reservas;
    private final UsuarioDoToken usuarioDoToken;
    private final AgendaDeRecursos agenda;
    private final RegistroAuditoriaRepository auditoria;
    private final Clock clock;

    public AlterarHorarioReserva(ReservaRepository reservas, UsuarioDoToken usuarioDoToken, AgendaDeRecursos agenda,
                                 RegistroAuditoriaRepository auditoria, Clock clock) {
        this.reservas = reservas;
        this.usuarioDoToken = usuarioDoToken;
        this.agenda = agenda;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public ReservaResumo executar(Long reservaId, String emailAutor, Instant inicio, Instant fim) {
        var novo = new PeriodoReserva(inicio, fim);
        var reserva = reservas.findById(reservaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reserva " + reservaId + " não encontrada."));
        var autor = usuarioDoToken.ativo(emailAutor);
        var recursos = reserva.getOcupacoes().stream().map(Ocupacao::getRecurso).toList();
        agenda.travarEmOrdem(recursos.stream().map(Recurso::getId).toList());

        var agora = clock.instant();
        // Validar ANTES de consultar conflitos: a consulta faria o Hibernate gravar antes uma mudança
        // ainda não validada, e a recusa do banco sairia sem a mensagem clara.
        reserva.exigirAlteravel(autor, novo, agora);
        agenda.garantirLivre(recursos, novo, reserva.getId());
        reserva.alterarPeriodo(autor, novo, agora);

        try {
            // Flush agora: o UPDATE com checagem de versão e a constraint rodam aqui, não no commit.
            reservas.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            if (ViolacaoDeExclusao.causou(e)) {
                throw agenda.conflitoNoBanco(recursos);
            }
            throw e;
        }
        reserva.retirarMudancas().forEach(m -> auditoria.save(RegistroAuditoria.daReserva(reserva.getId(), m)));
        return ReservaResumo.de(reserva);
    }
}
