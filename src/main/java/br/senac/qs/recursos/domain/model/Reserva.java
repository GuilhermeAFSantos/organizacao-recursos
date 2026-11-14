package br.senac.qs.recursos.domain.model;

import static br.senac.qs.recursos.domain.model.StatusReserva.APROVADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.CANCELADA;
import static br.senac.qs.recursos.domain.model.StatusReserva.CONCLUIDA;
import static br.senac.qs.recursos.domain.model.StatusReserva.EM_USO;
import static br.senac.qs.recursos.domain.model.StatusReserva.NAO_COMPARECEU;
import static br.senac.qs.recursos.domain.model.StatusReserva.REJEITADA;

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

    public static Reserva solicitar(Usuario solicitante, Recurso recurso, PeriodoReserva periodo,
                                    String finalidade, Instant agora) {
        var reserva = new Reserva();
        reserva.solicitante = solicitante;
        reserva.periodo = periodo;
        reserva.finalidade = finalidade;
        reserva.status = StatusReserva.SOLICITADA;
        reserva.criadoEm = agora;
        reserva.atualizadoEm = agora;
        reserva.ocupacoes.add(new Ocupacao(reserva, recurso, periodo));
        reserva.registrar(null, StatusReserva.SOLICITADA, solicitante.getId(), solicitante.getEmail(), null, agora);
        if (!recurso.isRestrito()) {
            reserva.aprovarAutomaticamente(agora);
        }
        return reserva;
    }

    /** D1: sem recurso restrito, não há quem precise aprovar; o sistema aprova e isso fica auditado. */
    private void aprovarAutomaticamente(Instant agora) {
        var anterior = status;
        this.status = APROVADA;
        this.motivoStatus = MOTIVO_APROVACAO_AUTOMATICA;
        registrar(anterior, APROVADA, null, MudancaDeEstado.AUTOR_SISTEMA, MOTIVO_APROVACAO_AUTOMATICA, agora);
    }

    public void aprovar(Usuario responsavel, Instant agora) {
        exigirResponsavel(responsavel, "aprovar");
        proibirDecisaoSobreAPropria(responsavel);
        exigirTransicaoPara(APROVADA);
        if (!agora.isBefore(periodo.inicio())) {
            throw new RegraNegocioException("A reserva só pode ser aprovada antes do horário de início.");
        }
        this.aprovador = responsavel;
        mudarPara(APROVADA, responsavel, null, agora);
    }

    public void rejeitar(Usuario responsavel, String motivo, Instant agora) {
        exigirResponsavel(responsavel, "rejeitar");
        proibirDecisaoSobreAPropria(responsavel);
        exigirTransicaoPara(REJEITADA);
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException("Informe o motivo da rejeição.");
        }
        mudarPara(REJEITADA, responsavel, motivo, agora);
    }

    public void cancelar(Usuario autor, String motivo, Instant agora) {
        if (!autor.mesmaPessoaQue(solicitante) && autor.getPerfil() != Perfil.ADMINISTRADOR) {
            throw new OperacaoNaoPermitidaException(
                    "Somente quem solicitou a reserva ou um ADMINISTRADOR pode cancelá-la.");
        }
        exigirTransicaoPara(CANCELADA);
        if (status == APROVADA && !agora.isBefore(periodo.inicio())) {
            throw new RegraNegocioException("Uma reserva aprovada só pode ser cancelada antes do horário de início.");
        }
        mudarPara(CANCELADA, autor, motivo, agora);
    }

    public void iniciarUso(Usuario responsavel, Instant agora) {
        exigirResponsavel(responsavel, "registrar o início de uso de");
        exigirTransicaoPara(EM_USO);
        mudarPara(EM_USO, responsavel, null, agora);
    }

    public void registrarNaoComparecimento(Usuario responsavel, String motivo, Instant agora) {
        exigirResponsavel(responsavel, "registrar o não comparecimento em");
        exigirTransicaoPara(NAO_COMPARECEU);
        if (agora.isBefore(periodo.inicio())) {
            throw new RegraNegocioException(
                    "O não comparecimento só pode ser registrado a partir do horário de início.");
        }
        mudarPara(NAO_COMPARECEU, responsavel, motivo, agora);
    }

    public void concluir(Usuario responsavel, Instant agora) {
        exigirResponsavel(responsavel, "concluir");
        exigirTransicaoPara(CONCLUIDA);
        mudarPara(CONCLUIDA, responsavel, null, agora);
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

    private void proibirDecisaoSobreAPropria(Usuario responsavel) {
        if (responsavel.mesmaPessoaQue(solicitante)) {
            throw new OperacaoNaoPermitidaException(
                    "Você não pode aprovar ou rejeitar uma reserva solicitada por você.");
        }
    }

    private void exigirTransicaoPara(StatusReserva destino) {
        if (!status.podeIrPara(destino)) {
            throw new TransicaoInvalidaException("Não é possível mudar a reserva de " + status + " para "
                    + destino + ". " + status.descreverOpcoes());
        }
    }

    private void mudarPara(StatusReserva novo, Usuario autor, String motivo, Instant agora) {
        var anterior = status;
        this.status = novo;
        this.motivoStatus = motivo;
        this.atualizadoEm = agora;
        if (novo.liberaHorario()) {
            ocupacoes.forEach(Ocupacao::liberar);
        }
        registrar(anterior, novo, autor.getId(), autor.getEmail(), motivo, agora);
    }

    private void registrar(StatusReserva anterior, StatusReserva novo, Long autorId, String autorEmail,
                           String motivo, Instant agora) {
        mudancasPendentes.add(new MudancaDeEstado(anterior, novo, autorId, autorEmail, motivo, agora));
    }

    public Long getId() {
        return id;
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
