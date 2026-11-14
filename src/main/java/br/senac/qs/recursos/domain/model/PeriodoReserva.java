package br.senac.qs.recursos.domain.model;

import java.time.Instant;

import br.senac.qs.recursos.domain.exception.RegraNegocioException;

/**
 * Intervalo de tempo de uma reserva: <b>value object</b> imutável e sempre válido.
 *
 * <p>A RC01 é verificada no construtor. Por isso não existe {@code PeriodoReserva} inválido em
 * memória: quem recebe um período não precisa validá-lo de novo.
 *
 * <p>Usa {@link Instant} (instante absoluto, sem fuso). Assim "10:00-03:00" e "13:00Z" são o
 * mesmo instante, e a comparação nunca depende de como o cliente escreveu o horário.
 */
public record PeriodoReserva(Instant inicio, Instant fim) {

    static final String INICIO_OBRIGATORIO = "O início da reserva é obrigatório.";
    static final String FIM_OBRIGATORIO = "O término da reserva é obrigatório.";
    static final String FIM_ANTES_DO_INICIO = "O término da reserva deve ser posterior ao início.";

    public PeriodoReserva {
        if (inicio == null) {
            throw new RegraNegocioException(INICIO_OBRIGATORIO);
        }
        if (fim == null) {
            throw new RegraNegocioException(FIM_OBRIGATORIO);
        }
        // RC01: "posterior" é estrito. Término igual ao início (duração zero) também é rejeitado.
        if (!fim.isAfter(inicio)) {
            throw new RegraNegocioException(FIM_ANTES_DO_INICIO);
        }
    }

    public boolean sobrepoe(PeriodoReserva outro) {
        return inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }
}
