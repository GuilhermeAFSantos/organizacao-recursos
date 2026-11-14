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

    private Material(String nome, boolean restrito) {
        super(nome, restrito);
    }

    public static Material cadastrar(Usuario autor, String nome, boolean restrito, String patrimonio,
                                     String tipoMaterial, EstadoConservacao estado) {
        exigirAdministrador(autor);
        var material = new Material(nome, restrito);
        material.preencher(nome, restrito, patrimonio, tipoMaterial, estado);
        return material;
    }

    public void alterar(Usuario autor, String nome, boolean restrito, String patrimonio, String tipoMaterial,
                        EstadoConservacao estado) {
        exigirAdministrador(autor);
        preencher(nome, restrito, patrimonio, tipoMaterial, estado);
    }

    private void preencher(String nome, boolean restrito, String patrimonio, String tipoMaterial,
                           EstadoConservacao estado) {
        definirDadosGerais(nome, restrito);
        this.patrimonio = obrigatorio(patrimonio, "Informe o patrimônio do material.");
        this.tipoMaterial = obrigatorio(tipoMaterial, "Informe o tipo do material.");
        this.estadoConservacao = informado(estado, "Informe o estado de conservação.");
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
