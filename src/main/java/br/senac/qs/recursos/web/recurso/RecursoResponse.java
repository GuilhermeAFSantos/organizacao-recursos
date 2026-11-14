package br.senac.qs.recursos.web.recurso;

import br.senac.qs.recursos.application.recurso.RecursoResumo;
import br.senac.qs.recursos.application.recurso.RecursoResumo.DetalhesMaterial;
import br.senac.qs.recursos.application.recurso.RecursoResumo.DetalhesProfessor;
import br.senac.qs.recursos.application.recurso.RecursoResumo.DetalhesSala;
import br.senac.qs.recursos.domain.model.TipoRecurso;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Recurso em JSON "plano": os dados comuns e, no mesmo nível, os campos do seu tipo (por exemplo,
 * {@code capacidade} só aparece em salas). Os detalhes dos outros tipos são nulos e ficam de fora.
 */
public record RecursoResponse(Long id, TipoRecurso tipo, String nome, boolean restrito, boolean ativo,
                              @JsonUnwrapped DetalhesSala sala,
                              @JsonUnwrapped DetalhesProfessor professor,
                              @JsonUnwrapped DetalhesMaterial material) {

    static RecursoResponse de(RecursoResumo r) {
        return new RecursoResponse(r.id(), r.tipo(), r.nome(), r.restrito(), r.ativo(), r.sala(), r.professor(),
                r.material());
    }
}
