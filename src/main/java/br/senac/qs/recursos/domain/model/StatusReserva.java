package br.senac.qs.recursos.domain.model;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Estados da reserva e a máquina de transições (RC05).
 *
 * <pre>
 * SOLICITADA ─► APROVADA ─► EM_USO ─► CONCLUIDA
 *     │            ├──────► CANCELADA
 *     │            └──────► NAO_COMPARECEU
 *     ├──────► REJEITADA
 *     └──────► CANCELADA
 * </pre>
 *
 * Estados finais não têm saída. Qualquer transição fora desta tabela é recusada.
 */
public enum StatusReserva {
    SOLICITADA, APROVADA, EM_USO, CONCLUIDA, REJEITADA, CANCELADA, NAO_COMPARECEU;

    /** Para quais estados é permitido ir a partir deste. Fonte única da tabela de transições. */
    public Set<StatusReserva> destinos() {
        return switch (this) {
            case SOLICITADA -> Set.of(APROVADA, REJEITADA, CANCELADA);
            case APROVADA -> Set.of(EM_USO, CANCELADA, NAO_COMPARECEU);
            case EM_USO -> Set.of(CONCLUIDA);
            case CONCLUIDA, REJEITADA, CANCELADA, NAO_COMPARECEU -> Set.of();
        };
    }

    public boolean podeIrPara(StatusReserva destino) {
        return destinos().contains(destino);
    }

    /** Texto para o usuário com as saídas possíveis a partir deste estado. */
    public String descreverOpcoes() {
        if (destinos().isEmpty()) {
            return this + " é um estado final.";
        }
        return "A partir de " + this + ", as opções são: "
                + destinos().stream().sorted().map(Enum::name).collect(Collectors.joining(", ")) + ".";
    }

    /** Estados em que a reserva deixa de ocupar o horário, que fica livre para outra reserva. */
    public boolean liberaHorario() {
        return this == REJEITADA || this == CANCELADA || this == NAO_COMPARECEU;
    }
}
