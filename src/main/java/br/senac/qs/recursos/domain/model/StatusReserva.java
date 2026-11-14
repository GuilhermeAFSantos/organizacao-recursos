package br.senac.qs.recursos.domain.model;

/** Estados da reserva (RC05). As transições entre eles chegam na fatia da máquina de estados. */
public enum StatusReserva {
    SOLICITADA, APROVADA, EM_USO, CONCLUIDA, REJEITADA, CANCELADA, NAO_COMPARECEU
}
