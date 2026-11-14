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
