package br.senac.qs.recursos.domain.model;

import static br.senac.qs.recursos.domain.model.StatusReserva.APROVADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.CANCELADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.CONCLUIDA;
import static br.senac.qs.recursos.domain.model.StatusReserva.EM_USO;
import static br.senac.qs.recursos.domain.model.StatusReserva.NAO_COMPARECEU;
import static br.senac.qs.recursos.domain.model.StatusReserva.REJEITADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.SOLICITADA;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.senac.qs.recursos.domain.exception.OperacaoNaoPermitidaException;
import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import br.senac.qs.recursos.domain.exception.TransicaoInvalidaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

@Entity
@Table(name = "reserva")
public class Reserva {

    static final String MOTIVO_APROVACAO_AUTOMATICA = "Nenhum recurso é restrito: aprovação automática.";


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id")
    private Usuario solicitante;

    @Embedded
    private PeriodoReserva periodo;

    private String finalidade;

    @Enumerated(EnumType.STRING)
    private StatusReserva status;

    @Version
    private long version;

    @Column(name = "criado_em")
    private Instant criadoEm;

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.PERSIST)
    private List<Ocupacao> ocupacoes = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprovador_id")
    private Usuario aprovador;

    @Column(name = "motivo_status")
    private String motivoStatus;

    /** Mudanças ainda não gravadas na auditoria; o caso de uso as retira e persiste. */
    @Transient
    private final List<MudancaDeEstado> mudancasPendentes = new ArrayList<>();

    protected Reserva() {
    }

    /** Reserva de um único recurso (atalho para a forma com lista). */
    public static Reserva solicitar(Usuario solicitante, Recurso recurso, PeriodoReserva periodo,
                                    String finalidade, Instant agora) {
        return solicitar(solicitante, List.of(recurso), periodo, finalidade, agora);
    }

    /**
     * Reserva de sala, professor e materiais juntos (RF04, RF05): tudo ou nada. Cada recurso
     * ganha uma ocupação no mesmo período; a aprovação automática só vale se nenhum for restrito.
     */
    public static Reserva solicitar(Usuario solicitante, List<Recurso> recursos, PeriodoReserva periodo,
                                    String finalidade, Instant agora) {
        if (recursos.isEmpty()) {
            throw new RegraNegocioException("Informe ao menos um recurso para reservar.");
        }
        // Q12: como na alteração de horário, não se reserva o que já começou ou passou.
        if (!agora.isBefore(periodo.inicio())) {
            throw new RegraNegocioException("A reserva deve começar no futuro.");
        }
        recursos.forEach(Recurso::exigirReservavel);
        var reserva = new Reserva();
        reserva.solicitante = solicitante;
        reserva.periodo = periodo;
        reserva.finalidade = finalidade;
        reserva.status = StatusReserva.SOLICITADA;
        reserva.criadoEm = agora;
        reserva.atualizadoEm = agora;
        recursos.forEach(recurso -> reserva.ocupacoes.add(new Ocupacao(reserva, recurso, periodo)));
        reserva.registrar(null, StatusReserva.SOLICITADA, solicitante.getId(), solicitante.getEmail(), null, agora);
        if (!reserva.temRecursoRestrito()) {
            reserva.aprovarAutomaticamente(agora);
        }
        return reserva;
    }

    /**
     * Muda o horário (RF04). Período e ocupações mudam juntos, na mesma transação. Se a reserva
     * estava APROVADA e tem recurso restrito, volta a SOLICITADA: a aprovação valia para o horário
     * antigo. É o único caminho que volta um estado, e fica auditado como ALTERACAO.
     */
    public void alterarPeriodo(Usuario autor, PeriodoReserva novo, Instant agora) {
        exigirAlteravel(autor, novo, agora);
        var periodoAnterior = this.periodo;
        var estadoAnterior = this.status;
        this.periodo = novo;
        ocupacoes.forEach(ocupacao -> ocupacao.moverPara(novo));
        this.atualizadoEm = agora;
        var motivo = "Horário alterado de " + periodoAnterior.descrever() + " para " + novo.descrever() + ".";
        mudancasPendentes.add(new MudancaDeEstado(MudancaDeEstado.ALTERACAO, estadoAnterior, status, autor.getId(),
                autor.getEmail(), motivo, agora));
    }

    /**
     * Verifica, SEM alterar nada, se a alteração é permitida. O caso de uso chama antes de consultar
     * conflitos, para a consulta não gravar uma mudança ainda não validada. Ordem: quem pode →
     * estado → horário atual → horário novo.
     */
    public void exigirAlteravel(Usuario autor, PeriodoReserva novo, Instant agora) {
        if (!autor.mesmaPessoaQue(solicitante) && autor.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new OperacaoNaoPermitidaException(
                    "Somente quem solicitou a reserva ou um ADMINISTRADOR pode alterá-la.");
        }
        if (status != SOLICITADA && status != APROVADA) {
            throw new RegraNegocioException("Só é possível alterar o horário de reservas solicitadas ou aprovadas.");
        }
        if (!agora.isBefore(periodo.inicio())) {
            throw new RegraNegocioException("O horário só pode ser alterado antes do início da reserva.");
        }
        if (!agora.isBefore(novo.inicio())) {
            throw new RegraNegocioException("O novo horário deve começar no futuro.");
        }
    }

    /** RF07/RC06: basta um recurso restrito para a reserva inteira precisar de aprovação. */
    private boolean temRecursoRestrito() {
        return ocupacoes.stream().anyMatch(ocupacao -> ocupacao.getRecurso().isRestrito());
    }

    /** D1: sem recurso restrito, não há quem precise aprovar; o sistema aprova e isso fica auditado. */
    private void aprovarAutomaticamente(Instant agora) {
        mudarPara(APROVADA, null, MudancaDeEstado.AUTOR_SISTEMA, MOTIVO_APROVACAO_AUTOMATICA, agora);
    }

    /** O recurso faz parte desta reserva? Compara por id: o recurso da ocupação pode ser um proxy. */
    public boolean incluiRecurso(Recurso recurso) {
        return ocupacoes.stream().map(Ocupacao::getRecurso)
                .anyMatch(r -> r == recurso || (r.getId() != null && r.getId().equals(recurso.getId())));
    }

    public void aprovar(Usuario responsavel, Instant agora) {
        exigirResponsavel(responsavel, "aprovar");
        proibirDecisaoSobreAPropria(responsavel);
        this.aprovador = responsavel;
        mudarPara(APROVADA, responsavel, null, agora);
    }

    public void rejeitar(Usuario responsavel, String motivo, Instant agora) {
        exigirResponsavel(responsavel, "rejeitar");
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException("Informe o motivo da rejeição.");
        }
        mudarPara(REJEITADA, responsavel, motivo, agora);
    }

    public void cancelar(Usuario autor, String motivo, Instant agora) {
        // Prática de TDD estrito: regra removida, a ser refeita cenário a cenário.
    }

    public void iniciarUso(Usuario responsavel, Instant agora) {
        // Prática de TDD estrito: regra removida, a ser refeita cenário a cenário.
    }

    public void registrarNaoComparecimento(Usuario responsavel, String motivo, Instant agora) {
        // Prática de TDD estrito: regra removida, a ser refeita cenário a cenário.
    }

    public void concluir(Usuario responsavel, Instant agora) {
        // Prática de TDD estrito: regra removida, a ser refeita cenário a cenário.
    }

    public List<MudancaDeEstado> retirarMudancas() {
        var retiradas = List.copyOf(mudancasPendentes);
        mudancasPendentes.clear();
        return retiradas;
    }

    private static void exigirResponsavel(Usuario autor, String acao) {
        if (autor.getPerfil() != Perfil.RESPONSAVEL) {
            throw new OperacaoNaoPermitidaException("Somente um RESPONSAVEL pode " + acao + " reservas.");
        }
    }

    /** Segregação de funções: ninguém decide sobre a reserva que ele mesmo pediu. */
    private void proibirDecisaoSobreAPropria(Usuario responsavel) {
        if (responsavel.mesmaPessoaQue(solicitante)) {
            throw new OperacaoNaoPermitidaException(
                    "Você não pode aprovar ou rejeitar uma reserva solicitada por você.");
        }
    }

    /** Muda o estado e registra a mudança para a auditoria (RC08), sempre do mesmo jeito. */
    private void mudarPara(StatusReserva novo, Long autorId, String autorEmail, String motivo, Instant agora) {
        var anterior = status;
        this.status = novo;
        this.motivoStatus = motivo;
        this.atualizadoEm = agora;
        if (novo.liberaHorario()) {
            ocupacoes.forEach(Ocupacao::liberar);
        }
        registrar(anterior, novo, autorId, autorEmail, motivo, agora);
    }

    private void mudarPara(StatusReserva novo, Usuario autor, String motivo, Instant agora) {
        mudarPara(novo, autor.getId(), autor.getEmail(), motivo, agora);
    }

    private void registrar(StatusReserva anterior, StatusReserva novo, Long autorId, String autorEmail,
                           String motivo, Instant agora) {
        mudancasPendentes.add(new MudancaDeEstado(anterior, novo, autorId, autorEmail, motivo, agora));
    }

    public Long getId() {
        return id;
    }

    public Usuario getSolicitante() {
        return solicitante;
    }

    public PeriodoReserva getPeriodo() {
        return periodo;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public StatusReserva getStatus() {
        return status;
    }

    public Usuario getAprovador() {
        return aprovador;
    }

    public String getMotivoStatus() {
        return motivoStatus;
    }

    public List<Ocupacao> getOcupacoes() {
        return List.copyOf(ocupacoes);
    }
}
