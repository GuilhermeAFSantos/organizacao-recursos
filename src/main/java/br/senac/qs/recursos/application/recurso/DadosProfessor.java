package br.senac.qs.recursos.application.recurso;

import java.util.List;

/** Professor é sempre restrito (D4), por isso não há o campo. */
public record DadosProfessor(String nome, String matricula, String email, List<String> competencias) {

    public DadosProfessor {
        competencias = competencias == null ? List.of() : List.copyOf(competencias);
    }
}
