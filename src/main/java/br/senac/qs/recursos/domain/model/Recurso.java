package br.senac.qs.recursos.domain.model;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "recurso")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo")
public abstract class Recurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    /** RF07/RC06: reservas deste recurso exigem aprovação de um RESPONSAVEL. */
    private boolean restrito;

    private boolean ativo;

    /** Lock otimista: duas alterações simultâneas do mesmo cadastro não se sobrescrevem em silêncio. */
    @Version
    private long version;

    protected Recurso() {
    }

    protected Recurso(String nome, boolean restrito) {
        this.nome = nome;
        this.restrito = restrito;
        this.ativo = true;
    }

    public abstract TipoRecurso getTipo();

    public void desativar(Usuario autor) {
        throw new UnsupportedOperationException("a implementar");
    }

    public void reativar(Usuario autor) {
        throw new UnsupportedOperationException("a implementar");
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public boolean isRestrito() {
        return restrito;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
