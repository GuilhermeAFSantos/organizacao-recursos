package br.senac.qs.recursos.domain.model;

/** Estados da reserva (RC05). */
public enum StatusReserva {
    SOLICITADA, APROVADA, EM_USO, CONCLUIDA, REJEITADA, CANCELADA, NAO_COMPARECEU;

    public boolean podeIrPara(StatusReserva destino) {
        return switch (this) {
            case SOLICITADA -> destino == APROVADA || destino == REJEITADA || destino == CANCELADA;
            case APROVADA -> destino == EM_USO || destino == CANCELADA || destino == NAO_COMPARECEU;
            case EM_USO -> destino == CONCLUIDA;
            case CONCLUIDA, REJEITADA, CANCELADA, NAO_COMPARECEU -> false;
        };
    }

    public boolean liberaHorario() {
        return this == REJEITADA || this == CANCELADA || this == NAO_COMPARECEU;
    }
}
