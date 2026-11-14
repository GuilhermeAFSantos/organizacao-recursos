package br.senac.qs.recursos.application.recurso;

import java.util.List;

import br.senac.qs.recursos.domain.model.EstadoConservacao;
import br.senac.qs.recursos.domain.model.Material;
import br.senac.qs.recursos.domain.model.Professor;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.Sala;
import br.senac.qs.recursos.domain.model.TipoRecurso;
import br.senac.qs.recursos.domain.model.TipoSala;

/** Visão de leitura de um recurso: dados comuns e, conforme o tipo, os detalhes de um só dos três. */
public record RecursoResumo(Long id, TipoRecurso tipo, String nome, boolean restrito, boolean ativo,
                            DetalhesSala sala, DetalhesProfessor professor, DetalhesMaterial material) {

    public record DetalhesSala(int capacidade, String bloco, int andar, TipoSala tipoSala) {
    }

    public record DetalhesProfessor(String matricula, String email, List<String> competencias) {
    }

    public record DetalhesMaterial(String patrimonio, String tipoMaterial, EstadoConservacao estadoConservacao) {
    }

    public static RecursoResumo de(Recurso recurso) {
        return switch (recurso) {
            case Sala s -> comum(s, new DetalhesSala(s.getCapacidade(), s.getBloco(), s.getAndar(), s.getTipoSala()),
                    null, null);
            case Professor p -> comum(p, null,
                    new DetalhesProfessor(p.getMatricula(), p.getEmail(), p.nomesDasCompetencias()), null);
            case Material m -> comum(m, null, null,
                    new DetalhesMaterial(m.getPatrimonio(), m.getTipoMaterial(), m.getEstadoConservacao()));
            default -> throw new IllegalStateException("Tipo de recurso desconhecido: " + recurso.getClass());
        };
    }

    private static RecursoResumo comum(Recurso r, DetalhesSala sala, DetalhesProfessor professor,
                                       DetalhesMaterial material) {
        return new RecursoResumo(r.getId(), r.getTipo(), r.getNome(), r.isRestrito(), r.isAtivo(), sala, professor,
                material);
    }
}
