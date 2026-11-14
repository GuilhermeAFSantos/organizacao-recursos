package br.senac.qs.recursos.domain.model;

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
        throw new UnsupportedOperationException("a implementar");
    }

    public static String chave(String nome) {
        throw new UnsupportedOperationException("a implementar");
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
}
