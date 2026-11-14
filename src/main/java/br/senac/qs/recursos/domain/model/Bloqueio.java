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

/** Período em que um recurso não pode ser reservado: manutenção ou bloqueio administrativo (RF08, RC04). */
@Entity
@Table(name = "bloqueio")
public class Bloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TipoBloqueio tipo;

    private String motivo;

    @Embedded
    private PeriodoReserva periodo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Column(name = "criado_em")
    private Instant criadoEm;

    @OneToMany(mappedBy = "bloqueio", cascade = CascadeType.PERSIST)
    private List<Ocupacao> ocupacoes = new ArrayList<>();

    protected Bloqueio() {
    }

    public static Bloqueio registrar(Usuario administrador, Recurso recurso, TipoBloqueio tipo,
                                     PeriodoReserva periodo, String motivo, Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public void encerrar(Usuario administrador, Instant agora) {
        throw new UnsupportedOperationException("ainda não implementado");
    }

    public boolean isAtivo() {
        return false;
    }

    public Long getId() {
        return id;
    }

    public TipoBloqueio getTipo() {
        return tipo;
    }

    public String getMotivo() {
        return motivo;
    }

    public PeriodoReserva getPeriodo() {
        return periodo;
    }

    public List<Ocupacao> getOcupacoes() {
        return List.copyOf(ocupacoes);
    }
}
