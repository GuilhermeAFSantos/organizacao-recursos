package br.senac.qs.recursos.domain.model;

import java.time.Instant;

/**
 * Uma mudança na reserva, com tudo o que a auditoria exige (RC08): o quê, quem, quando, estado
 * anterior, estado novo e motivo. {@code anterior} é nulo na criação. {@code autorId} é nulo quando
 * o autor é o próprio sistema (aprovação automática).
 *
 * @param acao {@link #CRIACAO}, {@link #TRANSICAO} de estado ou {@link #ALTERACAO} de horário
 */
public record MudancaDeEstado(String acao, StatusReserva anterior, StatusReserva novo, Long autorId,
                              String autorEmail, String motivo, Instant quando) {

    public static final String AUTOR_SISTEMA = "sistema";
    public static final String CRIACAO = "CRIACAO";
    public static final String TRANSICAO = "TRANSICAO";
    public static final String ALTERACAO = "ALTERACAO";

    /** Criação (sem estado anterior) ou transição de estado. */
    public MudancaDeEstado(StatusReserva anterior, StatusReserva novo, Long autorId, String autorEmail,
                           String motivo, Instant quando) {
        this(anterior == null ? CRIACAO : TRANSICAO, anterior, novo, autorId, autorEmail, motivo, quando);
    }
}
