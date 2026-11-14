package br.senac.qs.recursos.web.reserva;

import jakarta.validation.constraints.Size;

/** Corpo das ações em que o motivo é opcional (cancelamento, não comparecimento). */
public record MotivoOpcionalRequest(@Size(max = 500) String motivo) {

    static String de(MotivoOpcionalRequest requisicao) {
        return requisicao == null ? null : requisicao.motivo();
    }
}
