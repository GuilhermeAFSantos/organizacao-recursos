package br.senac.qs.recursos.domain.model;

import java.time.Instant;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ocupacao")
public class Ocupacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurso_id")
    private Recurso recurso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bloqueio_id")
    private Bloqueio bloqueio;

    @Embedded
    private PeriodoReserva periodo;

    private boolean bloqueante;

    protected Ocupacao() {
    }

    Ocupacao(Reserva reserva, Recurso recurso, PeriodoReserva periodo) {
        this.reserva = reserva;
        this.recurso = recurso;
        this.periodo = periodo;
        this.bloqueante = true;
    }

    Ocupacao(Bloqueio bloqueio, Recurso recurso, PeriodoReserva periodo) {
        this.bloqueio = bloqueio;
        this.recurso = recurso;
        this.periodo = periodo;
        this.bloqueante = true;
    }

    public Recurso getRecurso() {
        return recurso;
    }

    public PeriodoReserva getPeriodo() {
        return periodo;
    }

    /** Encurta o período, mantendo o início (bloqueio encerrado antes do previsto). */
    void encurtarAte(Instant novoFim) {
        this.periodo = new PeriodoReserva(periodo.inicio(), novoFim);
    }

    public boolean isBloqueante() {
        return bloqueante;
    }

    /** O horário deixa de estar ocupado (reserva rejeitada, cancelada ou com não comparecimento). */
    void liberar() {
        this.bloqueante = false;
    }
}
