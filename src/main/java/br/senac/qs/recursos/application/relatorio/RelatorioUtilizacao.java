package br.senac.qs.recursos.application.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.senac.qs.recursos.domain.model.OrigemConflito;
import br.senac.qs.recursos.domain.model.TipoRecurso;

/**
 * Relatório de utilização (RF12) de um período de dias (horário de Brasília, fim incluído).
 *
 * @param recursos só os recursos com alguma atividade no período, do mais ao menos usado
 */
public record RelatorioUtilizacao(LocalDate inicio, LocalDate fim, Totais totais, List<LinhaRecurso> recursos) {

    /** Por reserva distinta; os conflitos incluem os de recurso desconhecido (recusa do banco). */
    public record Totais(long reservas, BigDecimal horasReservadas, long naoComparecimentos, long conflitosEvitados,
                         Map<OrigemConflito, Long> conflitosPorOrigem) {
    }

    /** Para um PROFESSOR, {@code horasReservadas} é a carga horária no período. */
    public record LinhaRecurso(Long recursoId, String nome, TipoRecurso tipo, long reservas,
                               BigDecimal horasReservadas, long naoComparecimentos, long conflitosEvitados) {
    }
}
