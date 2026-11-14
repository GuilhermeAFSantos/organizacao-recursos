package br.senac.qs.recursos.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/** Professor como recurso reservável: a agenda dele não pode ter sobreposição (RF05). */
@Entity
@Table(name = "professor")
@DiscriminatorValue("PROFESSOR")
@PrimaryKeyJoinColumn(name = "id")
public class Professor extends Recurso {

    private String matricula;

    private String email;

    @ManyToMany
    @JoinTable(name = "professor_competencia",
            joinColumns = @JoinColumn(name = "professor_id"),
            inverseJoinColumns = @JoinColumn(name = "competencia_id"))
    private Set<Competencia> competencias = new HashSet<>();

    protected Professor() {
    }

    public static Professor cadastrar(Usuario autor, String nome, boolean restrito, String matricula, String email,
                                      Set<Competencia> competencias) {
        throw new UnsupportedOperationException("a implementar");
    }

    public void alterar(Usuario autor, String nome, boolean restrito, String matricula, String email,
                        Set<Competencia> competencias) {
        throw new UnsupportedOperationException("a implementar");
    }

    @Override
    public TipoRecurso getTipo() {
        return TipoRecurso.PROFESSOR;
    }

    public String getMatricula() {
        return matricula;
    }

    public String getEmail() {
        return email;
    }

    public List<String> nomesDasCompetencias() {
        throw new UnsupportedOperationException("a implementar");
    }
}
