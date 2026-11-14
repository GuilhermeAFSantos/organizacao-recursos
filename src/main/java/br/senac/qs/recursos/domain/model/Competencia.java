package br.senac.qs.recursos.domain.model;

import java.util.Locale;

import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Competência de um professor (ex.: "Java"), usada na pesquisa (RF03). */
@Entity
@Table(name = "competencia")
public class Competencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    protected Competencia() {
    }

    public static Competencia nova(String nome) {
        var competencia = new Competencia();
        competencia.nome = normalizar(nome);
        return competencia;
    }

    /**
     * Chave de comparação: "Java", " JAVA " e "java" são a mesma competência (o banco garante com o
     * índice único em {@code lower(nome)}).
     */
    public static String chave(String nome) {
        return normalizar(nome).toLowerCase(Locale.ROOT);
    }

    /** Sem espaços nas pontas e com um único espaço entre as palavras. */
    private static String normalizar(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("Informe o nome da competência.");
        }
        return nome.strip().replaceAll("\\s+", " ");
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
}
