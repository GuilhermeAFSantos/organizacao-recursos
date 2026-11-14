package br.senac.qs.recursos.domain.model;

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
}
