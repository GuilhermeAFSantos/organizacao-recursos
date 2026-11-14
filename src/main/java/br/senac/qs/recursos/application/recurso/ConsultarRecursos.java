package br.senac.qs.recursos.application.recurso;

import br.senac.qs.recursos.application.Pagina;
import br.senac.qs.recursos.domain.exception.RecursoNaoEncontradoException;
import br.senac.qs.recursos.domain.pesquisa.FiltroRecursos;
import br.senac.qs.recursos.domain.repository.PesquisaDeRecursos;
import br.senac.qs.recursos.domain.repository.RecursoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta (RF02) e pesquisa (RF03) de recursos, abertas a qualquer usuário logado. */
@Service
public class ConsultarRecursos {

    /** Ordem estável: o id desempata nomes iguais, para um item não pular de uma página para outra. */
    private static final Sort ORDEM = Sort.by("nome", "id");

    private final RecursoRepository recursos;

    public ConsultarRecursos(RecursoRepository recursos) {
        this.recursos = recursos;
    }

    @Transactional(readOnly = true)
    public RecursoResumo buscar(Long id) {
        return recursos.findById(id).map(RecursoResumo::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recurso " + id + " não encontrado."));
    }

    /** Só recursos ativos. Os limites de página e tamanho são validados na entrada da API. */
    @Transactional(readOnly = true)
    public Pagina<RecursoResumo> pesquisar(FiltroRecursos filtro, int pagina, int tamanho) {
        var resultado = recursos.findAll(PesquisaDeRecursos.para(filtro), PageRequest.of(pagina, tamanho, ORDEM));
        return Pagina.de(resultado, RecursoResumo::de);
    }
}
