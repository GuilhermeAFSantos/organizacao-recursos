package br.senac.qs.recursos.domain.model;

import java.time.Instant;

import br.senac.qs.recursos.domain.exception.RegraNegocioException;
import jakarta.persistence.Embeddable;

/**
 * Intervalo de tempo de uma reserva: <b>value object</b> imutável e sempre válido.
 *
 * <p>A RC01 é verificada no construtor. Por isso não existe {@code PeriodoReserva} inválido em
 * memória: quem recebe um período não precisa validá-lo de novo.
 *
 * <p>Usa {@link Instant} (instante absoluto, sem fuso). Assim "10:00-03:00" e "13:00Z" são o
 * mesmo instante, e a comparação nunca depende de como o cliente escreveu o horário.
 */
@Embeddable
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

    /**
     * RC02: dois períodos se sobrepõem quando cada um começa <b>antes</b> do outro terminar.
     *
     * <p>Os intervalos são <b>semiabertos</b>, {@code [inicio, fim)}: o instante final não faz parte
     * do período. Por isso, períodos que apenas se tocam (08h–10h e 10h–12h) NÃO se sobrepõem.
     *
     * <p>É a mesma semântica do operador {@code &&} sobre {@code tstzrange(inicio, fim, '[)')} usado
     * na constraint de exclusão do banco (V1). Aplicação e banco precisam concordar; caso
     * contrário a aplicação aceitaria o que o banco recusa, ou o contrário.
     */
    public boolean sobrepoe(PeriodoReserva outro) {
        return this.inicio.isBefore(outro.fim) && outro.inicio.isBefore(this.fim);
    }
}
