package br.senac.qs.recursos.application.relatorio;

import static br.senac.qs.recursos.domain.model.PeriodoReserva.BRASILIA;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import br.senac.qs.recursos.application.relatorio.RelatorioUtilizacao.LinhaRecurso;
import br.senac.qs.recursos.application.relatorio.RelatorioUtilizacao.Totais;
import br.senac.qs.recursos.domain.model.OrigemConflito;
import br.senac.qs.recursos.domain.model.TipoRecurso;
import br.senac.qs.recursos.domain.repository.RelatorioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatório de utilização, carga horária e conflitos evitados (RF12). Os dias são do horário de
 * Brasília: de {@code inicio} 00:00 até o fim de {@code fim} (intervalo semiaberto, como as reservas).
 */
@Service
public class GerarRelatorioUtilizacao {

    private static final BigDecimal SEGUNDOS_POR_HORA = BigDecimal.valueOf(3600);

    private final RelatorioRepository relatorios;

    public GerarRelatorioUtilizacao(RelatorioRepository relatorios) {
        this.relatorios = relatorios;
    }

    @Transactional(readOnly = true)
    public RelatorioUtilizacao gerar(LocalDate inicio, LocalDate fim) {
        var de = inicio.atStartOfDay(BRASILIA).toInstant();
        var ate = fim.plusDays(1).atStartOfDay(BRASILIA).toInstant();

        Map<Long, Linha> linhas = new LinkedHashMap<>();
        for (var u : relatorios.utilizacaoPorRecurso(de, ate)) {
            var linha = linhas.computeIfAbsent(u.getRecursoId(), id -> new Linha(id, u.getNome(), u.getTipo()));
            linha.reservas = u.getReservas();
            linha.horas = horas(u.getSegundos());
            linha.naoComparecimentos = u.getNaoComparecimentos();
        }
        Map<OrigemConflito, Long> porOrigem = new EnumMap<>(OrigemConflito.class);
        for (var origem : OrigemConflito.values()) {
            porOrigem.put(origem, 0L);
        }
        for (var c : relatorios.conflitosEvitados(de, ate)) {
            porOrigem.merge(OrigemConflito.valueOf(c.getOrigem()), c.getQuantidade(), Long::sum);
            if (c.getRecursoId() != null) {
                linhas.computeIfAbsent(c.getRecursoId(), id -> new Linha(id, c.getNome(), c.getTipo()))
                        .conflitos += c.getQuantidade();
            }
        }

        var t = relatorios.totais(de, ate);
        var totais = new Totais(t.getReservas(), horas(t.getSegundos()), t.getNaoComparecimentos(),
                porOrigem.values().stream().mapToLong(Long::longValue).sum(), porOrigem);
        var recursos = linhas.values().stream().map(Linha::paraResumo)
                .sorted(Comparator.comparing(LinhaRecurso::horasReservadas).reversed()
                        .thenComparing(LinhaRecurso::nome))
                .toList();
        return new RelatorioUtilizacao(inicio, fim, totais, recursos);
    }

    private static BigDecimal horas(BigDecimal segundos) {
        return segundos.divide(SEGUNDOS_POR_HORA, 2, RoundingMode.HALF_UP);
    }

    /** Acumulador de uma linha (utilização e conflitos vêm de consultas diferentes). */
    private static final class Linha {
        private final Long recursoId;
        private final String nome;
        private final String tipo;
        private long reservas;
        private BigDecimal horas = BigDecimal.ZERO.setScale(2);
        private long naoComparecimentos;
        private long conflitos;

        private Linha(Long recursoId, String nome, String tipo) {
            this.recursoId = recursoId;
            this.nome = nome;
            this.tipo = tipo;
        }

        private LinhaRecurso paraResumo() {
            return new LinhaRecurso(recursoId, nome, TipoRecurso.valueOf(tipo), reservas, horas, naoComparecimentos,
                    conflitos);
        }
    }
}
