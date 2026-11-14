package br.senac.qs.recursos.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Retirada ou devolução de um item de material (RF09). Histórico somente de inserção: uma vez
 * registrada, a movimentação não muda (a V5 também impede UPDATE e DELETE no banco).
 */
@Entity
@Immutable
@Table(name = "movimentacao_material")
public class MovimentacaoMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id")
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    @Enumerated(EnumType.STRING)
    private TipoMovimentacao tipo;

    @Column(name = "data_hora")
    private Instant dataHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conservacao")
    private EstadoConservacao estadoConservacao;

    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    protected MovimentacaoMaterial() {
    }

    public static MovimentacaoMaterial retirar(Reserva reserva, Material material, Usuario autor,
                                               MovimentacaoMaterial ultima, String observacao, Instant agora) {
        throw new UnsupportedOperationException("a implementar");
    }

    public static MovimentacaoMaterial devolver(Reserva reserva, Material material, Usuario autor,
                                                MovimentacaoMaterial ultima, EstadoConservacao estado,
                                                String observacao, Instant agora) {
        throw new UnsupportedOperationException("a implementar");
    }

    public boolean isAtrasada() {
        throw new UnsupportedOperationException("a implementar");
    }

    public Long getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public Reserva getReserva() {
        return reserva;
    }

    public TipoMovimentacao getTipo() {
        return tipo;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public EstadoConservacao getEstadoConservacao() {
        return estadoConservacao;
    }

    public String getObservacao() {
        return observacao;
    }

    public Usuario getRegistradoPor() {
        return registradoPor;
    }
}
