package br.senac.qs.recursos.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    public void aprovar(Usuario responsavel, Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void rejeitar(Usuario responsavel, String motivo, Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void cancelar(Usuario autor, String motivo, Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void iniciarUso(Usuario responsavel, Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void registrarNaoComparecimento(Usuario responsavel, String motivo, Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void concluir(Usuario responsavel, Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public List<MudancaDeEstado> retirarMudancas() {
        return List.of();
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
        return reserva;
    }

    public Long getId() {
        return id;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public StatusReserva getStatus() {
        return status;
    }
}
