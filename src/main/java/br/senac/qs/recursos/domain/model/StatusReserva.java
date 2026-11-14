package br.senac.qs.recursos.domain.model;

/** Estados da reserva (RC05). */
public enum StatusReserva {
    SOLICITADA, APROVADA, EM_USO, CONCLUIDA, REJEITADA, CANCELADA, NAO_COMPARECEU;

    public boolean podeIrPara(StatusReserva destino) {
        return false;
    }

    public boolean liberaHorario() {
        return false;
    }
}
