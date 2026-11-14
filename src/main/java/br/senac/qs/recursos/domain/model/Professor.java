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

    private Professor(String nome, boolean restrito) {
        super(nome, restrito);
    }

    /**
     * D4 (Q13): todo professor é RESTRITO. O enunciado manda o RESPONSAVEL "validar a alocação de
     * docentes", então reservar um professor sempre passa por aprovação. Não há opção no cadastro.
     */
    public static Professor cadastrar(Usuario autor, String nome, String matricula, String email,
                                      Set<Competencia> competencias) {
        exigirAdministrador(autor);
        var professor = new Professor(nome, true);
        professor.preencher(nome, matricula, email, competencias);
        return professor;
    }

    public void alterar(Usuario autor, String nome, String matricula, String email, Set<Competencia> competencias) {
        exigirAdministrador(autor);
        preencher(nome, matricula, email, competencias);
    }

    private void preencher(String nome, String matricula, String email, Set<Competencia> competencias) {
        definirDadosGerais(nome, true);
        this.matricula = obrigatorio(matricula, "Informe a matrícula do professor.");
        this.email = email == null || email.isBlank() ? null : Usuario.normalizarEmail(email);
        this.competencias.clear();
        this.competencias.addAll(competencias);
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

    /** Nomes das competências em ordem alfabética (ignorando maiúsculas), para exibição estável. */
    public List<String> nomesDasCompetencias() {
        return competencias.stream().map(Competencia::getNome).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
