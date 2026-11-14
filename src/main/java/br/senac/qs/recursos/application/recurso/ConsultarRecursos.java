package br.senac.qs.recursos.application.recurso;

import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de recursos (RF02), aberta a qualquer usuário logado. */
@Service
public class ConsultarRecursos {

    private final RecursoRepository recursos;

    public ConsultarRecursos(RecursoRepository recursos) {
        this.recursos = recursos;
    }

    @Transactional(readOnly = true)
    public RecursoResumo buscar(Long id) {
        return recursos.findById(id).map(RecursoResumo::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recurso " + id + " não encontrado."));
    }
}
