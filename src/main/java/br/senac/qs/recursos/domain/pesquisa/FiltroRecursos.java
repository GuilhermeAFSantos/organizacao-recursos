package br.senac.qs.recursos.domain.pesquisa;

import br.senac.qs.recursos.domain.model.PeriodoReserva;
import br.senac.qs.recursos.domain.model.TipoRecurso;
import br.senac.qs.recursos.domain.model.TipoSala;

/**
 * Critérios da pesquisa de recursos (RF03). Todos são opcionais (nulo = não filtra) e se combinam
 * com "e". Filtros de um tipo específico (capacidade, competência, tipo de material) restringem o
 * resultado àquele tipo. Com {@code periodo}, só entram recursos livres no intervalo inteiro.
 * Localização de sala = {@code bloco} + {@code andar}.
 */
public record FiltroRecursos(TipoRecurso tipo, Integer capacidadeMinima, String bloco, Integer andar,
                             TipoSala tipoSala, String competencia, String tipoMaterial, PeriodoReserva periodo) {

    public FiltroRecursos {
        bloco = vazioComoNulo(bloco);
        competencia = vazioComoNulo(competencia);
        tipoMaterial = vazioComoNulo(tipoMaterial);
    }

    public boolean filtraSala() {
        return capacidadeMinima != null || bloco != null || andar != null || tipoSala != null;
    }

    private static String vazioComoNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
