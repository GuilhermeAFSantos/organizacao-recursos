package br.senac.qs.recursos.application.bloqueio;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import br.senac.qs.recursos.application.UsuarioDoToken;
import br.senac.qs.recursos.application.ViolacaoDeExclusao;
import br.senac.qs.recursos.domain.exception.ConflitoHorarioException;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.model.Bloqueio;
import br.senac.qs.recursos.domain.model.Ocupacao;
import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.RegistroAuditoria;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.repository.BloqueioRepository;
import br.senac.qs.recursos.domain.repository.OcupacaoRepository;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import br.senac.qs.recursos.domain.repository.RegistroAuditoriaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de bloqueios de recursos: manutenção e bloqueio administrativo (RF08, RC04, Q9, Q10).
 *
 * <p>Decisão D3: bloqueio sobre reservas ativas é RECUSADO, listando as reservas em conflito; o
 * ADMINISTRADOR decide o que cancelar. Usa o mesmo lock de recurso das reservas
 * ({@link RecursoRepository#travar}), o que serializa reserva × bloqueio simultâneos (MN-09).
 */
@Service
public class GerenciarBloqueios {

    private final UsuarioDoToken usuarioDoToken;
    private final RecursoRepository recursos;
    private final OcupacaoRepository ocupacoes;
    private final BloqueioRepository bloqueios;
    private final RegistroAuditoriaRepository auditoria;
    private final Clock clock;

    public GerenciarBloqueios(UsuarioDoToken usuarioDoToken, RecursoRepository recursos, OcupacaoRepository ocupacoes,
                              BloqueioRepository bloqueios, RegistroAuditoriaRepository auditoria, Clock clock) {
        this.usuarioDoToken = usuarioDoToken;
        this.recursos = recursos;
        this.ocupacoes = ocupacoes;
        this.bloqueios = bloqueios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public BloqueioResumo registrar(RegistrarBloqueioComando comando) {
        var periodo = new PeriodoReserva(comando.inicio(), comando.fim());
        var autor = usuarioDoToken.ativo(comando.emailAutor());
        var recurso = recursoAtivoTravado(comando.recursoId());
        var agora = clock.instant();
        var bloqueio = Bloqueio.registrar(autor, recurso, comando.tipo(), periodo, comando.motivo(), agora);
        garantirPeriodoLivre(recurso, periodo);

        var gravado = gravar(bloqueio, recurso);
        auditoria.save(RegistroAuditoria.doBloqueio(gravado.getId(), "CRIACAO", null, "ATIVO", autor,
                comando.motivo(), agora));
        return BloqueioResumo.de(gravado);
    }

    @Transactional
    public BloqueioResumo encerrar(Long bloqueioId, String emailAutor) {
        var bloqueio = bloqueios.findById(bloqueioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio " + bloqueioId + " não encontrado."));
        var autor = usuarioDoToken.ativo(emailAutor);
        var agora = clock.instant();

        bloqueio.encerrar(autor, agora);

        bloqueios.saveAndFlush(bloqueio);
        auditoria.save(RegistroAuditoria.doBloqueio(bloqueio.getId(), "ENCERRAMENTO", "ATIVO", "ENCERRADO", autor,
                null, agora));
        return BloqueioResumo.de(bloqueio);
    }

    private Recurso recursoAtivoTravado(Long recursoId) {
        var naoEncontrado = new RecursoNaoEncontradoException("Recurso " + recursoId + " não encontrado.");
        recursos.travar(recursoId).orElseThrow(() -> naoEncontrado);
        return recursos.findById(recursoId).orElseThrow(() -> naoEncontrado);
    }

    /** D3: não bloqueia por cima de reservas ativas; informa quais são, para o ADMINISTRADOR decidir. */
    private void garantirPeriodoLivre(Recurso recurso, PeriodoReserva periodo) {
        List<Ocupacao> conflitos = ocupacoes.sobrepostas(recurso.getId(), periodo.inicio(), periodo.fim());
        if (conflitos.isEmpty()) {
            return;
        }
        List<Long> reservasEmConflito = conflitos.stream().map(Ocupacao::getReserva).filter(Objects::nonNull)
                .map(Reserva::getId).toList();
        if (reservasEmConflito.isEmpty()) {
            throw new ConflitoHorarioException("Já existe um bloqueio de " + recurso.getNome() + " nesse período.");
        }
        throw new ConflitoHorarioException(recurso.getNome() + " tem " + reservasEmConflito.size()
                + " reserva(s) ativa(s) nesse período. Cancele-as antes de bloquear ou escolha outro período.",
                reservasEmConflito);
    }

    private Bloqueio gravar(Bloqueio bloqueio, Recurso recurso) {
        try {
            return bloqueios.saveAndFlush(bloqueio);
        } catch (DataIntegrityViolationException e) {
            if (ViolacaoDeExclusao.causou(e)) {
                throw new ConflitoHorarioException(recurso.getNome() + " já está ocupado nesse período.");
            }
            throw e;
        }
    }
}
