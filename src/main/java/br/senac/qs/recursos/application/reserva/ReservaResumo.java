package br.senac.qs.recursos.application.reserva;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import br.senac.qs.recursos.domain.model.Ocupacao;
import br.senac.qs.recursos.domain.model.Recurso;
import br.senac.qs.recursos.domain.model.Reserva;
import br.senac.qs.recursos.domain.model.StatusReserva;
import br.senac.qs.recursos.domain.model.TipoRecurso;

/**
 * Visão da reserva devolvida pelos casos de uso (sem expor a entidade fora da transação).
 *
 * @param professorId nulo quando a reserva não inclui professor
 * @param materiaisIds em ordem crescente; vazia quando não há materiais
 * @param motivo motivo da última mudança de estado (ex.: da rejeição), quando houver
 * @param recursos todos os recursos, pelo nome, na ordem sala, professor, materiais
 */
public record ReservaResumo(Long id, Long salaId, Long professorId, List<Long> materiaisIds, Instant inicio,
                            Instant fim, String finalidade, StatusReserva status, String solicitante, String motivo,
                            List<RecursoDaReserva> recursos) {

    public record RecursoDaReserva(Long id, TipoRecurso tipo, String nome) {
    }

    /** Monta o resumo DENTRO da transação (lê as ocupações, carregadas sob demanda). */
    static ReservaResumo de(Reserva reserva) {
        var ocupacoes = reserva.getOcupacoes();
        var periodo = reserva.getPeriodo();
        var recursos = ocupacoes.stream().map(Ocupacao::getRecurso)
                .map(r -> new RecursoDaReserva(r.getId(), r.getTipo(), r.getNomeParaExibicao()))
                .sorted(Comparator.comparing(RecursoDaReserva::tipo).thenComparing(RecursoDaReserva::id))
                .toList();
        return new ReservaResumo(reserva.getId(), primeiroDoTipo(ocupacoes, TipoRecurso.SALA),
                primeiroDoTipo(ocupacoes, TipoRecurso.PROFESSOR), idsDoTipo(ocupacoes, TipoRecurso.MATERIAL),
                periodo.inicio(), periodo.fim(), reserva.getFinalidade(), reserva.getStatus(),
                reserva.getSolicitante().getEmail(), reserva.getMotivoStatus(), recursos);
    }

    private static Long primeiroDoTipo(List<Ocupacao> ocupacoes, TipoRecurso tipo) {
        return idsDoTipo(ocupacoes, tipo).stream().findFirst().orElse(null);
    }

    private static List<Long> idsDoTipo(List<Ocupacao> ocupacoes, TipoRecurso tipo) {
        return ocupacoes.stream().map(Ocupacao::getRecurso).filter(r -> r.getTipo() == tipo)
                .map(Recurso::getId).sorted().toList();
    }
}
