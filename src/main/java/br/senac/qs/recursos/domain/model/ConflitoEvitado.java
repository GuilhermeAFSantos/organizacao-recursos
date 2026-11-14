package br.senac.qs.recursos.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Uma reserva (ou alteração) recusada por sobreposição: o sistema evitou uma dupla alocação (RF12).
 * Guarda o horário PEDIDO. Ids em vez de associações: é um registro estatístico, gravado depois do
 * rollback da tentativa, e não precisa carregar as entidades.
 */
@Entity
@Immutable
@Table(name = "conflito_evitado")
public class ConflitoEvitado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo quando o banco recusou uma reserva de vários recursos sem dizer qual conflitou. */
    @Column(name = "recurso_id")
    private Long recursoId;

    @Column(name = "solicitante_id")
    private Long solicitanteId;

    @Embedded
    private PeriodoReserva periodo;

    @Enumerated(EnumType.STRING)
    private OrigemConflito origem;

    @Column(name = "ocorrido_em")
    private Instant ocorridoEm;

    protected ConflitoEvitado() {
    }

    public static ConflitoEvitado registrar(Long recursoId, Long solicitanteId, PeriodoReserva periodo,
                                            OrigemConflito origem, Instant quando) {
        var conflito = new ConflitoEvitado();
        conflito.recursoId = recursoId;
        conflito.solicitanteId = solicitanteId;
        conflito.periodo = periodo;
        conflito.origem = origem;
        conflito.ocorridoEm = quando;
        return conflito;
    }

    public Long getRecursoId() {
        return recursoId;
    }

    public Long getSolicitanteId() {
        return solicitanteId;
    }

    public PeriodoReserva getPeriodo() {
        return periodo;
    }

    public OrigemConflito getOrigem() {
        return origem;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}
