package br.senac.qs.recursos.application.recurso;

import java.util.List;

public record DadosProfessor(String nome, boolean restrito, String matricula, String email,
                             List<String> competencias) {

    public DadosProfessor {
        competencias = competencias == null ? List.of() : List.copyOf(competencias);
    }
}
