package br.senac.qs.recursos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/** Item de material com patrimônio próprio (unitário: a mesma regra anti-sobreposição das salas vale). */
@Entity
@Table(name = "material")
@DiscriminatorValue("MATERIAL")
@PrimaryKeyJoinColumn(name = "id")
public class Material extends Recurso {

    private String patrimonio;

    @Column(name = "tipo_material")
    private String tipoMaterial;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conservacao")
    private EstadoConservacao estadoConservacao;

    protected Material() {
    }

    public static Material cadastrar(Usuario autor, String nome, boolean restrito, String patrimonio,
                                     String tipoMaterial, EstadoConservacao estado) {
        throw new UnsupportedOperationException("a implementar");
    }

    public void alterar(Usuario autor, String nome, boolean restrito, String patrimonio, String tipoMaterial,
                        EstadoConservacao estado) {
        throw new UnsupportedOperationException("a implementar");
    }

    @Override
    public TipoRecurso getTipo() {
        return TipoRecurso.MATERIAL;
    }

    public String getPatrimonio() {
        return patrimonio;
    }

    public String getTipoMaterial() {
        return tipoMaterial;
    }

    public EstadoConservacao getEstadoConservacao() {
        return estadoConservacao;
    }
}
